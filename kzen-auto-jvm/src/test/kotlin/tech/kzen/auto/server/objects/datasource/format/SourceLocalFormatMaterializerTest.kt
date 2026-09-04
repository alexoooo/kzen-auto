package tech.kzen.auto.server.objects.datasource.format

import tech.kzen.auto.common.data.format.DelimitedFormatOverrideConventions
import tech.kzen.auto.common.data.format.FormatMaterializationRequest
import tech.kzen.auto.common.data.format.FormatMaterializationResult
import tech.kzen.auto.common.data.format.FormatOverrideEditorMetadata
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.common.data.read.HeaderReadSpec
import tech.kzen.auto.common.data.schema.AuthoredRecordSchemaNotation
import tech.kzen.auto.server.data.read.delimited.ConfiguredDelimitedReaderCapability
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionIndex
import tech.kzen.lib.common.model.structure.notation.PositionedObjectPath
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.platform.collect.toPersistentMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue


class SourceLocalFormatMaterializerTest {
    @Test
    fun configuredDelimitedAuthoringAppliesStrictCanonicalOverrides() {
        val format = ConfiguredDelimitedTestFormats.csv()
        @Suppress("DEPRECATION")
        val read = format.resolvedRead(DataRef(null, "orders.csv"))
        val request = FormatMaterializationRequest(
            "formats.yaml#Csv",
            read,
            null,
            mapOf(
                DelimitedFormatOverrideConventions.delimiter to ";",
                DelimitedFormatOverrideConventions.header to "infer-labels",
                DelimitedFormatOverrideConventions.encoding to "utf-8",
                DelimitedFormatOverrideConventions.skipLeadingLines to "2",
                DelimitedFormatOverrideConventions.commentPrefix to null))

        val result = ConfiguredDelimitedReaderCapability.materialize(request)

        assertEquals("formats.yaml#Csv", result.formatBody.text("is"))
        assertEquals(";", result.formatBody.text("delimiter"))
        assertEquals("infer-labels", result.formatBody.text("header"))
        assertEquals("UTF-8", result.formatBody.text("charset"))
        assertEquals("2", result.formatBody.text("skipLeadingLines"))
        assertEquals("", result.formatBody.text("commentPrefix"))
        assertEquals(listOf("identity"), result.formatBody.textList("contentCodings"))

        for (invalid in listOf("many", "-1")) {
            assertFailsWith<IllegalArgumentException> {
                ConfiguredDelimitedReaderCapability.materialize(request.copy(
                    overrides = mapOf(DelimitedFormatOverrideConventions.skipLeadingLines to invalid)))
            }
        }
    }


    @Test
    @Suppress("DEPRECATION")
    fun configuredDelimitedAuthoringFreezesGzipIndependentlyOfTheFutureFilename() {
        val detected = ConfiguredDelimitedTestFormats.csv()
            .resolvedRead(DataRef(null, "orders.csv.gz"))

        val result = ConfiguredDelimitedReaderCapability.materialize(FormatMaterializationRequest(
            "formats.yaml#Csv",
            detected,
            observedSchema = null))
        val authoredCodings = result.formatBody.textList("contentCodings")
        val future = ConfiguredDelimitedTestFormats.csv(contentCodings = authoredCodings)
            .resolvedRead(DataRef(null, "renamed-without-gzip-extension.csv"))

        assertEquals(listOf("gzip"), authoredCodings)
        assertEquals(detected.contentCodings, future.contentCodings)
    }


    @Test
    fun configuredDelimitedColumnLockAuthorsTheObservedContractAndPositionalPolicy() {
        val format = ConfiguredDelimitedTestFormats.csv()
        @Suppress("DEPRECATION")
        val read = format.resolvedRead(DataRef(null, "orders.csv"))
        val decoded = ConfiguredDelimitedReaderCapability.decode(read.config) as DelimitedReadConfig
        val inferredLabels = ConfiguredDelimitedReaderCapability.encode(
            decoded.copy(header = HeaderReadSpec("infer-labels", "exact-name")))
        val inferredRead = read.copy(
            config = inferredLabels,
            configDigest = inferredLabels.digest())
        val observed = DataContract(DataType.Record(listOf(
            DataField(FieldId("c0"), DataType.Scalar(ScalarKind.Text)),
            DataField(FieldId("c1"), DataType.Scalar(ScalarKind.Integer(32))))))

        val result = ConfiguredDelimitedReaderCapability.materialize(FormatMaterializationRequest(
            "formats.yaml#Csv",
            inferredRead,
            observedSchema = observed))

        assertEquals("absent", result.formatBody.text("header"))
        assertEquals("schema", result.schemaReferenceAttribute)
        val schemaBody = requireNotNull(result.schemaBody)
        assertEquals(
            AuthoredRecordSchemaNotation.prototypeReference,
            schemaBody.text("is"))
        val fields = schemaBody.map.getValue(AttributeSegment.ofKey("fields")) as MapAttributeNotation
        assertEquals(listOf("c0", "c1"), fields.map.keys.map(AttributeSegment::asKey))
    }


    @Test
    fun allocationReusesOnlyACompleteIdenticalBodyAndSuffixesCollisions() {
        val documentPath = DocumentPath.parse("main/materializer-test.yaml")
        val source = ObjectLocation(documentPath, ObjectPath.parse("main.sources/input"))
        val body = body("base" to "one", "delimiter" to ",")
        val materialized = FormatMaterializationResult(
            body,
            null,
            null,
            FormatOverrideEditorMetadata("editors.yaml#Delimited", "Delimited options"),
            "UTF-8")
        val emptyGraph = GraphNotation.empty.withNewDocument(documentPath, DocumentNotation.empty)
        val allocated = SourceLocalFormatMaterializer.prepare(
            emptyGraph, source, "C:/data/orders.csv", materialized)
        val allocatedLocation = ObjectLocation.parse(allocated.formatReference)
        assertTrue(allocatedLocation.objectPath.startsWith(source.objectPath))

        val identicalGraph = graphWith(
            emptyGraph,
            allocatedLocation,
            ObjectNotation(AttributeNameMap(body.map.entries.associate { (segment, value) ->
                AttributeName(segment.asKey()) to value
            }.toPersistentMap())))
        val reused = SourceLocalFormatMaterializer.prepare(
            identicalGraph, source, "C:/data/orders.csv", materialized)
        assertEquals(allocated.formatReference, reused.formatReference)

        val collisionGraph = graphWith(
            emptyGraph,
            allocatedLocation,
            ObjectNotation(AttributeNameMap(body("base" to "different").map.entries.associate {
                (segment, value) -> AttributeName(segment.asKey()) to value
            }.toPersistentMap())))
        val suffixed = SourceLocalFormatMaterializer.prepare(
            collisionGraph, source, "C:/data/orders.csv", materialized)
        assertNotEquals(allocated.formatReference, suffixed.formatReference)
        assertTrue(ObjectLocation.parse(suffixed.formatReference).objectPath.startsWith(source.objectPath))
    }


    @Test
    fun schemaIsAllocatedBeforeItsReferenceIsBoundIntoTheReusableFormatBody() {
        val documentPath = DocumentPath.parse("main/materializer-schema-test.yaml")
        val source = ObjectLocation(documentPath, ObjectPath.parse("main.sources/input"))
        val schemaBody = body("is" to "auto-common/common-document.yaml#AuthoredRecordSchema")
        val materialized = FormatMaterializationResult(
            body("is" to "formats.yaml#Csv"),
            schemaBody,
            "schema",
            FormatOverrideEditorMetadata("editors.yaml#Delimited", "Delimited options"),
            "UTF-8")
        val emptyGraph = GraphNotation.empty.withNewDocument(documentPath, DocumentNotation.empty)

        val allocated = SourceLocalFormatMaterializer.prepare(
            emptyGraph, source, "C:/data/orders.csv", materialized)
        val schemaReference = requireNotNull(allocated.schemaReference)
        assertEquals(schemaReference, allocated.formatBody.values.getValue("schema").get())

        val withSchema = graphWith(
            emptyGraph,
            ObjectLocation.parse(schemaReference),
            ObjectNotation(AttributeNameMap(schemaBody.map.entries.associate { (segment, value) ->
                AttributeName(segment.asKey()) to value
            }.toPersistentMap())))
        val formatBody = objectNotation(allocated.formatBody)
        val completeGraph = graphWith(
            withSchema,
            ObjectLocation.parse(allocated.formatReference),
            formatBody)

        val reused = SourceLocalFormatMaterializer.prepare(
            completeGraph, source, "C:/data/orders.csv", materialized)
        assertEquals(allocated.schemaReference, reused.schemaReference)
        assertEquals(allocated.formatReference, reused.formatReference)
    }


    private fun graphWith(
        graph: GraphNotation,
        location: ObjectLocation,
        notation: ObjectNotation
    ): GraphNotation {
        val document = graph.documents.map.getValue(location.documentPath)
        return graph.withModifiedDocument(
            location.documentPath,
            document.withNewObject(
                PositionedObjectPath(location.objectPath, PositionIndex.zero),
                notation))
    }


    private fun body(vararg values: Pair<String, String>): MapAttributeNotation =
        MapAttributeNotation(values.associate { (key, value) ->
            AttributeSegment.ofKey(key) to ScalarAttributeNotation(value)
        }.toPersistentMap())


    private fun MapAttributeNotation.text(key: String): String =
        (map.getValue(AttributeSegment.ofKey(key)) as ScalarAttributeNotation).value


    private fun MapAttributeNotation.textList(key: String): List<String> =
        (map.getValue(AttributeSegment.ofKey(key)) as ListAttributeNotation)
            .values.map { requireNotNull(it.asString()) }


    private fun objectNotation(body: tech.kzen.lib.common.exec.MapExecutionValue): ObjectNotation =
        ObjectNotation(AttributeNameMap(body.values.map { (key, value) ->
            AttributeName(key) to ScalarAttributeNotation(value.get().toString())
        }.toMap().toPersistentMap()))
}
