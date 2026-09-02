package tech.kzen.auto.common.objects.document.custom

import tech.kzen.auto.common.data.schema.RecordSchema
import tech.kzen.auto.common.data.schema.AuthoredRecordSchema
import tech.kzen.auto.common.objects.document.custom.create.CustomCreation
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaConventions
import tech.kzen.auto.server.objects.custom.test.AdhocNamed
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.notation.NotationReducer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull


class CustomConventionsTest {
    private val documentPath = DocumentPath.parse("test/custom/custom-view-model-test.yaml")
    private val recordPrototype = ObjectLocation(documentPath, ObjectPath.parse("ThirdPartyRecordSchema"))
    private val novelPrototype = ObjectLocation(documentPath, ObjectPath.parse("NovelPrototype"))
    private val nameAttributeName = AttributeName("name")


    @Test
    fun recordSchemaSubtypeCreatesFromInheritedCapabilityDescriptor() {
        val notation = AutoTestUtils.readNotation()
        val creation = creation(notationStructure(notation), recordPrototype)

        assertEquals("Schemas", creation.category)
        assertEquals("Record schema", creation.label)
        assertNotNull(creation.body.get(DataSchemaConventions.fieldsAttributeName))
        assertConfigurationOnly(creation)

        val createdLocation = ObjectLocation(documentPath, ObjectPath.parse("main.objects/CreatedSchema"))
        val createdNotation = add(notation, createdLocation, creation)
        val metadata = assertNotNull(
            AutoTestUtils.graphMetadata(createdNotation).objectMetadata[createdLocation])
        val fieldsMetadata = assertNotNull(
            metadata.attributes.map[DataSchemaConventions.fieldsAttributeName])
        assertEquals(
            "DataSchemaFieldsEditor",
            fieldsMetadata.attributeMetadataNotation[AttributeSegment.ofKey("editor")]?.asString())
        assertEquals(
            AuthoredRecordSchema::class.qualifiedName,
            createdNotation.firstAttribute(createdLocation, NotationConventions.classAttributeName)?.asString())

        val instance = create(createdNotation, createdLocation)
        val schema = assertIs<RecordSchema>(instance)
        val record = assertIs<DataType.Record>(schema.contract().structural)
        assertEquals(listOf("amount"), record.fields.map { it.id.name })
    }


    @Test
    fun newCapabilityCategoryCreatesWithoutCatalogueBranch() {
        val notation = AutoTestUtils.readNotation()
        val creation = creation(notationStructure(notation), novelPrototype)

        assertEquals("Widgets", creation.category)
        assertEquals("Named widget", creation.label)
        assertEquals("created", creation.body.get(nameAttributeName)?.asString())
        assertConfigurationOnly(creation)

        val createdLocation = ObjectLocation(documentPath, ObjectPath.parse("main.objects/CreatedWidget"))
        val createdNotation = add(notation, createdLocation, creation)
        val metadata = assertNotNull(
            AutoTestUtils.graphMetadata(createdNotation).objectMetadata[createdLocation])
        assertNotNull(metadata.attributes.map[nameAttributeName])
        val instance = assertIs<AdhocNamed>(create(createdNotation, createdLocation))
        assertEquals("created", instance.name())
    }


    private fun creation(graphStructure: GraphStructure, location: ObjectLocation): CustomCreation {
        return CustomConventions.listPrototypes(graphStructure).single { it.prototype == location }
    }


    private fun notationStructure(notation: GraphNotation): GraphStructure {
        return GraphStructure(notation, AutoTestUtils.graphMetadata(notation))
    }


    private fun add(
        notation: GraphNotation,
        location: ObjectLocation,
        creation: CustomCreation
    ): GraphNotation {
        return NotationReducer().applyStructural(
            notation,
            AddObjectCommand(location, PositionRelation.afterLast, creation.body)
        ).graphNotation
    }


    private fun create(
        notation: GraphNotation,
        location: ObjectLocation
    ): Any {
        val definition = AutoTestUtils.graphDefinitionAttempt(notation).transitiveSuccessful
        return assertNotNull(GraphCreator.createGraph(definition.filterTransitive(location))[location]).reference
    }


    private fun assertConfigurationOnly(creation: CustomCreation) {
        assertEquals(
            creation.prototype.toReference().asString(),
            creation.body.get(NotationConventions.isAttributeName)?.asString())
        assertNull(creation.body.get(NotationConventions.classAttributeName))
        assertNull(creation.body.get(NotationConventions.metaAttributeName))
        assertNull(creation.body.get(NotationConventions.definerAttributeName))
        assertNull(creation.body.get(NotationConventions.creatorAttributeName))
        assertNull(creation.body.get(CustomCreation.customCreateAttributeName))
    }
}
