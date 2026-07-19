package tech.kzen.auto.common.objects.document.report.spec.analysis.pivot

import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.codec.recordOf
import tech.kzen.lib.platform.collect.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals


/**
 * Pins the PivotSpec NotationCodec (and its nested value-table / value-column codecs) against hand-built
 * notation fixtures — the behaviour the prior `ofNotation` / `Definer` produced, including the `rows` dedup.
 */
class PivotSpecCodecTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun parsePivot() {
        val notation = recordOf(
            "rows" to ListAttributeNotation(persistentListOf(
                ScalarAttributeNotation("0|city"),
                ScalarAttributeNotation("0|year"))),
            "values" to recordOf(
                "0|amount" to ListAttributeNotation(persistentListOf(
                    ScalarAttributeNotation("Sum"),
                    ScalarAttributeNotation("Average")))))

        val expected = PivotSpec(
            HeaderListing(listOf(HeaderLabel.ofString("0|city"), HeaderLabel.ofString("0|year"))),
            PivotValueTableSpec(mapOf(
                HeaderLabel.ofString("0|amount") to
                    PivotValueColumnSpec(setOf(PivotValueType.Sum, PivotValueType.Average)))))

        assertEquals(expected, PivotSpec.codec.parse(notation))
        assertEquals(notation, PivotSpec.codec.unparse(expected))
    }


    @Test
    fun pivotRoundTrips() {
        val spec = PivotSpec(
            HeaderListing(listOf(HeaderLabel.ofString("0|a"))),
            PivotValueTableSpec(mapOf(
                HeaderLabel.ofString("0|b") to PivotValueColumnSpec(setOf(PivotValueType.Count)))))

        assertEquals(spec, PivotSpec.codec.parse(PivotSpec.codec.unparse(spec)))
    }


    @Test
    fun valueColumnRoundTrips() {
        val spec = PivotValueColumnSpec(setOf(PivotValueType.Min, PivotValueType.Max))
        assertEquals(spec, PivotValueColumnSpec.codec.parse(PivotValueColumnSpec.codec.unparse(spec)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun rowsAreDeduplicated() {
        // Duplicate row entries collapse to one — matching the prior `.toSet()` dedup, and required because
        // HeaderListing enforces uniqueness.
        val notation = recordOf(
            "rows" to ListAttributeNotation(persistentListOf(
                ScalarAttributeNotation("0|city"),
                ScalarAttributeNotation("0|city"))),
            "values" to MapAttributeNotation.empty)

        val parsed = PivotSpec.codec.parse(notation)
        assertEquals(listOf(HeaderLabel.ofString("0|city")), parsed.rows.values)
    }
}
