package tech.kzen.auto.client.objects.document.job.edit

import tech.kzen.auto.client.objects.document.job.source.DataSourceResolveStore
import tech.kzen.auto.client.objects.document.job.source.DataSourceShapeStore
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.common.data.model.DataManifest
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataResolveResult
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.data.schema.LegacyDataShapeBridge
import tech.kzen.lib.common.exec.data.shape.DataShapeResult
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.collect.toPersistentMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull


class JobUpstreamSchemaTest {
    private val source = ObjectLocation.parse("job.yaml#main.sources/source")
    private val provider = ObjectLocation.parse("job.yaml#main.workers/provider")
    private val sink = ObjectLocation.parse("job.yaml#main.workers/sink")
    private val unrelated = ObjectLocation.parse("job.yaml#unrelated")


    @Test
    fun liveSummaryPrecedesInspectedSource() {
        val live = tabular("live").itemType
        val inspected = JobUpstreamSchema.ContractResult.Available(tabular("inspected").itemType)

        assertEquals(
            JobUpstreamSchema.Result(
                JobUpstreamSchema.Provider.LiveSummary,
                JobUpstreamSchema.ContractResult.Available(live)),
            JobUpstreamSchema.choose(live, inspected))
    }


    @Test
    fun inspectedSourceIsFallbackBeforeNoProvider() {
        val inspected = JobUpstreamSchema.ContractResult.Available(tabular("inspected").itemType)

        assertEquals(
            JobUpstreamSchema.Result(JobUpstreamSchema.Provider.InspectedSource, inspected),
            JobUpstreamSchema.choose(null, inspected))
        assertNull(JobUpstreamSchema.choose(null, JobUpstreamSchema.ContractResult.Unavailable))
    }


    @Test
    fun fullColumnsPathUsesInheritedReadDefaults() {
        val item = part("item")
        val manifest = DataManifest(listOf(DataUnit.of(item)))
        val result = columns(
            graph("ReadWorker"),
            manifest,
            mapOf(item to settled(tabular("default-column"))))

        assertEquals(JobUpstreamSchema.Provider.InspectedSource, result?.provider)
        assertEquals(listOf("default-column"), result?.columns?.values?.map { it.text })
    }


    @Test
    fun fullColumnsPathIgnoresUnselectedInspectingAndFailedParts() {
        val main = part("main", "main")
        val inspecting = part("reference-inspecting", "reference")
        val failed = part("reference-failed", "reference")
        val manifest = DataManifest(listOf(DataUnit.of(main, inspecting, failed)))
        val result = columns(
            graph("ReadMainWorker"),
            manifest,
            linkedMapOf(
                main to settled(tabular("main-column")),
                inspecting to DataSourceShapeStore.PartState(true, null, null),
                failed to DataSourceShapeStore.PartState(false, null, "inspection failed")))

        assertEquals(JobUpstreamSchema.Provider.InspectedSource, result?.provider)
        assertEquals(listOf("main-column"), result?.columns?.values?.map { it.text })
    }


    @Test
    fun graphProjectionHonorsRoleAttributesAndSupersetOrder() {
        val mainA = part("main-a", "main")
        val ignored = part("reference-a", "reference")
        val mainB = part("main-b", "main")
        val manifest = DataManifest(listOf(
            DataUnit(linkedMapOf("date" to "2026"), listOf(mainA, ignored)),
            DataUnit(linkedMapOf("group" to "west"), listOf(mainB))))
        val shapes = mapOf(
            mainA to tabular("a", "shared"),
            ignored to tabular("must-not-appear"),
            mainB to tabular("shared", "b"))

        val projected = JobUpstreamSchema.projectInspectedLane(
            graph("ReadMainColumnsWorker"), provider, manifest, shapes)

        assertEquals(
            listOf("date", "group", "a", "shared", "b"),
            columns(projected))
    }


    @Test
    fun supersetPreservesFieldTypesAndMarksMissingFieldsOptional() {
        val first = part("first")
        val second = part("second")
        val manifest = DataManifest(listOf(DataUnit.of(first), DataUnit.of(second)))
        val firstShape = shape(DataField(FieldId("key"), DataType.Scalar(ScalarKind.Text)))
        val secondShape = shape(
            DataField(FieldId("key"), DataType.Scalar(ScalarKind.Text)),
            DataField(FieldId("value"), DataType.Scalar(ScalarKind.Decimal)))

        val projection = JobUpstreamSchema.projectInspectedLane(
            graph("ReadWorker"), provider, manifest, mapOf(first to firstShape, second to secondShape))
        val contract = assertIs<JobUpstreamSchema.ContractResult.Available>(projection).contract
        val fields = (contract.structural as DataType.Record).fields

        assertEquals(DataType.Scalar(ScalarKind.Text), fields[0].type)
        assertEquals(DataType.Scalar(ScalarKind.Decimal), fields[1].type)
        assertEquals(false, fields[0].optional)
        assertEquals(true, fields[1].optional)
    }


    @Test
    fun supersetReportsTypedFieldConflictWithBothContracts() {
        val first = part("first")
        val second = part("second")
        val manifest = DataManifest(listOf(DataUnit.of(first), DataUnit.of(second)))
        val projection = JobUpstreamSchema.projectInspectedLane(
            graph("ReadWorker"),
            provider,
            manifest,
            mapOf(
                first to shape(DataField(FieldId("value"), DataType.Scalar(ScalarKind.Text))),
                second to shape(DataField(FieldId("value"), DataType.Scalar(ScalarKind.Decimal)))))

        val error = assertIs<JobUpstreamSchema.ContractResult.Error>(projection)
        assertEquals(true, error.message.contains("first=DataContract"))
        assertEquals(true, error.message.contains("second=DataContract"))
    }


    @Test
    fun attributeColumnCollisionFailsClosed() {
        val item = part("item")
        val manifest = DataManifest(listOf(
            DataUnit(linkedMapOf("column" to "attribute"), listOf(item))))

        assertIs<JobUpstreamSchema.ContractResult.Error>(JobUpstreamSchema.projectInspectedLane(
            graph("ReadColumnsWorker"),
            provider,
            manifest,
            mapOf(item to tabular("column"))))
    }


    @Test
    fun blankRoleRequiresOneRolePerUnitAndExplicitRoleExcludesOthers() {
        val main = part("main", "main")
        val reference = part("reference", "reference")
        val manifest = DataManifest(listOf(DataUnit.of(main, reference)))
        val shapes = mapOf(main to tabular("main-column"), reference to tabular("reference-column"))

        assertEquals(JobUpstreamSchema.ContractResult.Unavailable, JobUpstreamSchema.projectInspectedLane(
            graph("ReadWorker"), provider, manifest, shapes))
        assertEquals(
            listOf("main-column"),
            columns(JobUpstreamSchema.projectInspectedLane(
                graph("ReadMainWorker"), provider, manifest, shapes)))
    }


    @Test
    fun strictMismatchAndUnitsModeDoNotAdvertiseColumns() {
        val first = part("first")
        val second = part("second")
        val manifest = DataManifest(listOf(DataUnit.of(first), DataUnit.of(second)))
        val shapes = mapOf(first to tabular("a"), second to tabular("b"))

        assertIs<JobUpstreamSchema.ContractResult.Error>(JobUpstreamSchema.projectInspectedLane(
            graph("ReadStrictWorker"), provider, manifest, shapes))
        assertEquals(JobUpstreamSchema.ContractResult.Unavailable, JobUpstreamSchema.projectInspectedLane(
            graph("ReadUnitsWorker"), provider, manifest, shapes))
    }


    @Test
    fun capabilityAndConventionalFieldsFailClosed() {
        val item = part("item")
        val manifest = DataManifest(listOf(DataUnit.of(item)))
        val shapes = mapOf(item to tabular("column"))
        val structure = graph("ReadWorker")

        assertEquals(JobUpstreamSchema.ContractResult.Unavailable, JobUpstreamSchema.projectInspectedLane(
            structure, unrelated, manifest, shapes))
        assertNull(JobUpstreamSchema.readProjectionConfig(
            structure, ObjectLocation.parse("job.yaml#incomplete")))
    }


    private fun columns(
        structure: GraphStructure,
        manifest: DataManifest,
        parts: Map<DataPart, DataSourceShapeStore.PartState>
    ): JobUpstreamSchema.Result? {
        val rest = ClientRestApi("unused")
        val resolveStore = DataSourceResolveStore(rest, mapOf(
            source to DataSourceResolveStore.State(
                false, DataResolveResult(manifest, emptyList()), null)))
        val key = DataSourceShapeStore.Key.of(source, manifest)
        val shapeStore = DataSourceShapeStore(rest, mapOf(
            key to DataSourceShapeStore.State(parts, null)))
        return JobUpstreamSchema.columns(
            structure, sink, emptyMap(), resolveStore, shapeStore)
    }


    private fun graph(providerArchetype: String): GraphStructure {
        val parser = YamlNotationParser()
        val types = """
            Job:
              abstract: true
            DataSource:
              abstract: true
              class: tech.kzen.auto.common.data.api.DataSource
            DataSourceShapeProvider:
              abstract: true
            ChannelOutput:
              abstract: true
              class: tech.kzen.auto.common.paradigm.job.api.ChannelOutput
            ChannelInput:
              abstract: true
              class: tech.kzen.auto.common.paradigm.job.api.ChannelInput
            Worker:
              abstract: true
            ReadWorker:
              abstract: true
              is:
                - Worker
                - DataSourceShapeProvider
              output: ""
              source: ""
              emit: items
              role: ""
              attributes: ignore
              schemaMode: superset
              meta:
                output:
                  is: ChannelOutput
                source:
                  is: DataSource
                  nullable: true
            ReadMainWorker:
              abstract: true
              is: ReadWorker
              role: main
            ReadColumnsWorker:
              abstract: true
              is: ReadWorker
              attributes: columns
            ReadMainColumnsWorker:
              abstract: true
              is: ReadMainWorker
              attributes: columns
            ReadStrictWorker:
              abstract: true
              is: ReadWorker
              schemaMode: strict
            ReadUnitsWorker:
              abstract: true
              is: ReadWorker
              emit: units
            SinkWorker:
              abstract: true
              is: Worker
              input: ""
              meta:
                input:
                  is: ChannelInput
            UnrelatedWorker:
              abstract: true
              source: ""
              emit: items
              role: ""
              attributes: ignore
              schemaMode: superset
              meta:
                source:
                  is: DataSource
                  nullable: true
            IncompleteProvider:
              abstract: true
              is: DataSourceShapeProvider
              source: ""
              meta:
                source:
                  is: DataSource
                  nullable: true
            FileDataSource:
              is: DataSource
        """.trimIndent()
        val job = """
            main:
              is: Job
            main.sources/source:
              is: types.yaml#FileDataSource
            main.workers/provider:
              is: types.yaml#$providerArchetype
              source: main.sources/source
            main.workers/sink:
              is: types.yaml#SinkWorker
            unrelated:
              is: types.yaml#UnrelatedWorker
              source: main.sources/source
            incomplete:
              is: types.yaml#IncompleteProvider
              source: main.sources/source
        """.trimIndent()
        val documents = mapOf(
            DocumentPath.parse("types.yaml") to
                DocumentNotation(parser.parseDocumentObjects(types), null),
            DocumentPath.parse("job.yaml") to
                DocumentNotation(parser.parseDocumentObjects(job), null))
        val notation = GraphNotation(DocumentPathMap(documents.toPersistentMap()))
        return GraphStructure(notation, NotationMetadataReader().read(notation))
    }


    private fun settled(shape: DataShape): DataSourceShapeStore.PartState {
        return DataSourceShapeStore.PartState(false, DataShapeResult.Observed(shape), null)
    }


    private fun part(id: String, role: String = "main"): DataPart {
        return DataPart(
            DataRole(role),
            DataRef(null, id),
            null,
            ResolvedReadSpec(
                ReaderCapabilityIdentity("test", "delimited", "1"),
                listOf(ContentCodingSpec.identity),
                MapExecutionValue(emptyMap())))
    }


    private fun tabular(vararg columns: String): DataShape {
        return LegacyDataShapeBridge.tabular(HeaderListing.ofUnique(columns.toList()))
    }


    private fun shape(vararg fields: DataField): DataShape {
        return DataShape(
            DataContract(DataType.Record(fields.toList())),
            tech.kzen.lib.common.exec.data.shape.ShapeProvenance.ProviderReported,
            tech.kzen.lib.common.exec.data.shape.ShapeStability.Stable)
    }


    private fun columns(result: JobUpstreamSchema.ContractResult): List<String>? {
        val contract = (result as? JobUpstreamSchema.ContractResult.Available)?.contract
            ?: return null
        val record = contract.structural as? DataType.Record
            ?: return null
        return record.fields.map { it.id.name }
    }
}
