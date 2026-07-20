package tech.kzen.auto.common.objects.document.data.spec

import tech.kzen.auto.common.objects.document.data.DataFormatConventions
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
 * Pins the DataFormat field-type notation: [FieldFormatSpec] parse/unparse must round-trip, including nested
 * generics and per-level nullability (the recursion is the part that can silently lose a level).
 */
class FieldFormatSpecTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val mainLocation = ObjectLocation(
        DocumentPath.parse("test/data-format-spec-test.yaml"), ObjectPath.parse("main"))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun simpleClassRoundTrips() {
        assertRoundTrip(FieldFormatSpec(TypeMetadata(ClassName("kotlin.String"), listOf(), false)))
    }


    @Test
    fun nullableRoundTrips() {
        assertRoundTrip(FieldFormatSpec(TypeMetadata(ClassName("kotlin.Int"), listOf(), true)))
    }


    @Test
    fun anyRoundTrips() {
        assertRoundTrip(FieldFormatSpec.any)
    }


    @Test
    fun nestedGenericsRoundTripWithPerLevelNullability() {
        // Map<String, List<Int?>>
        val spec = FieldFormatSpec(TypeMetadata(
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
            "city" to FieldFormatSpec(stringType).asNotation(),
            "population" to FieldFormatSpec(intType).asNotation())

        assertEquals(
            FieldFormatListSpec(mapOf(
                "city" to FieldFormatSpec(stringType),
                "population" to FieldFormatSpec(intType))),
            FieldFormatListSpec.ofAttributeNotation(notation))
    }


    @Test
    fun listSpecParsesEmpty() {
        assertEquals(
            FieldFormatListSpec(mapOf()),
            FieldFormatListSpec.ofAttributeNotation(MapAttributeNotation.empty))
    }


    @Test
    fun addCommandInsertsAnyFieldUnderFields() {
        val command = assertIs<InsertMapEntryInAttributeCommand>(
            FieldFormatListSpec.addCommand(mainLocation, "city"))

        assertEquals(mainLocation, command.objectLocation)
        assertEquals(DataFormatConventions.fieldsAttributePath, command.containingMap)
        assertEquals(PositionRelation.afterLast, command.indexInMap)
        assertEquals(AttributeSegment.ofKey("city"), command.mapKey)
        assertEquals(FieldFormatSpec.any.asNotation(), command.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun assertRoundTrip(spec: FieldFormatSpec) {
        assertEquals(spec, FieldFormatSpec.ofNotation(spec.asNotation()))
    }
}
