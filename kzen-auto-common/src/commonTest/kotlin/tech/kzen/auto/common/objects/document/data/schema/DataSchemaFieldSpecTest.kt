package tech.kzen.auto.common.objects.document.data.schema
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.codec.recordOf
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertMapEntryInAttributeCommand
import tech.kzen.lib.platform.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


/**
 * Pins the DataSchema field-type notation: [DataSchemaFieldSpec] parse/unparse must round-trip, including nested
 * generics and per-level nullability (the recursion is the part that can silently lose a level).
 */
class DataSchemaFieldSpecTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val mainLocation = ObjectLocation(
        DocumentPath.parse("test/data-format-spec-test.yaml"), ObjectPath.parse("main"))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun simpleClassRoundTrips() {
        assertRoundTrip(DataSchemaFieldSpec(TypeMetadata(ClassName("kotlin.String"), listOf(), false)))
    }


    @Test
    fun nullableRoundTrips() {
        assertRoundTrip(DataSchemaFieldSpec(TypeMetadata(ClassName("kotlin.Int"), listOf(), true)))
    }


    @Test
    fun anyRoundTrips() {
        assertRoundTrip(DataSchemaFieldSpec.any)
    }


    @Test
    fun nestedGenericsRoundTripWithPerLevelNullability() {
        // Map<String, List<Int?>>
        val spec = DataSchemaFieldSpec(TypeMetadata(
            ClassName("kotlin.collections.Map"),
            listOf(
                TypeMetadata(ClassName("kotlin.String"), listOf(), false),
                TypeMetadata(
                    ClassName("kotlin.collections.List"),
                    listOf(TypeMetadata(ClassName("kotlin.Int"), listOf(), true)),
                    false)),
            false))

        assertRoundTrip(spec)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun listSpecParsesTwoFields() {
        val stringType = TypeMetadata(ClassName("kotlin.String"), listOf(), false)
        val intType = TypeMetadata(ClassName("kotlin.Int"), listOf(), true)

        val notation = recordOf(
            "city" to DataSchemaFieldSpec(stringType).asNotation(),
            "population" to DataSchemaFieldSpec(intType).asNotation())

        assertEquals(
            DataSchemaFieldListSpec(mapOf(
                "city" to DataSchemaFieldSpec(stringType),
                "population" to DataSchemaFieldSpec(intType))),
            DataSchemaFieldListSpec.ofAttributeNotation(notation))
    }


    @Test
    fun listSpecParsesEmpty() {
        assertEquals(
            DataSchemaFieldListSpec(mapOf()),
            DataSchemaFieldListSpec.ofAttributeNotation(MapAttributeNotation.empty))
    }


    @Test
    fun addCommandInsertsAnyFieldUnderFields() {
        val command = assertIs<InsertMapEntryInAttributeCommand>(
            DataSchemaFieldListSpec.addCommand(mainLocation, "city"))

        assertEquals(mainLocation, command.objectLocation)
        assertEquals(DataSchemaConventions.fieldsAttributePath, command.containingMap)
        assertEquals(PositionRelation.afterLast, command.indexInMap)
        assertEquals(AttributeSegment.ofKey("city"), command.mapKey)
        assertEquals(DataSchemaFieldSpec.any.asNotation(), command.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun assertRoundTrip(spec: DataSchemaFieldSpec) {
        assertEquals(spec, DataSchemaFieldSpec.ofNotation(spec.asNotation()))
    }
}
