package tech.kzen.auto.common.objects.document.report.spec.filter

import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.codec.recordOf
import tech.kzen.lib.platform.collect.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals


/**
 * Pins the FilterSpec NotationCodec against hand-built notation fixtures — the behaviour the prior
 * hand-written `ofNotation` / `Definer` produced. Parse and unparse are both asserted so the codec is a
 * faithful, non-drifting replacement.
 */
class FilterSpecCodecTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun parsePopulatedFilter() {
        val notation = recordOf(
            "0|city" to recordOf(
                "type" to ScalarAttributeNotation("RequireAny"),
                "values" to ListAttributeNotation(persistentListOf(
                    ScalarAttributeNotation("London"),
                    ScalarAttributeNotation("Paris")))),
            "0|country" to recordOf(
                "type" to ScalarAttributeNotation("ExcludeAll"),
                "values" to ListAttributeNotation.empty))

        val expected = FilterSpec(mapOf(
            HeaderLabel.ofString("0|city") to
                ColumnFilterSpec(ColumnFilterType.RequireAny, setOf("London", "Paris")),
            HeaderLabel.ofString("0|country") to
                ColumnFilterSpec(ColumnFilterType.ExcludeAll, setOf())))

        assertEquals(expected, FilterSpec.codec.parse(notation))
        assertEquals(notation, FilterSpec.codec.unparse(expected))
    }


    @Test
    fun filterRoundTrips() {
        val spec = FilterSpec(mapOf(
            HeaderLabel.ofString("0|city") to
                ColumnFilterSpec(ColumnFilterType.RequireAny, setOf("A", "B")),
            HeaderLabel.ofString("0|region") to
                ColumnFilterSpec(ColumnFilterType.ExcludeAll, setOf("X"))))

        assertEquals(spec, FilterSpec.codec.parse(FilterSpec.codec.unparse(spec)))
    }


    @Test
    fun columnRoundTrips() {
        val spec = ColumnFilterSpec(ColumnFilterType.RequireAny, setOf("one", "two"))
        assertEquals(spec, ColumnFilterSpec.codec.parse(ColumnFilterSpec.codec.unparse(spec)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun emptyNotationMatchesPriorHandBuiltTemplate() {
        // Prior emptyNotation was {type: "RequireAny", values: []}; the codec's unparse must reproduce it.
        val notation = ColumnFilterSpec.emptyNotation
        assertEquals(ScalarAttributeNotation("RequireAny"), notation["type"])
        assertEquals(ListAttributeNotation.empty, notation["values"])
        assertEquals(ColumnFilterSpec.empty, ColumnFilterSpec.codec.parse(notation))
    }
}
