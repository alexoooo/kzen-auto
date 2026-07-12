package tech.kzen.auto.server.objects.target

import org.junit.Test
import tech.kzen.auto.common.objects.document.target.TargetCropMatches
import tech.kzen.auto.common.objects.document.target.TargetLocateResult
import tech.kzen.auto.common.objects.document.target.TargetMatchRect
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import kotlin.test.assertEquals


class TargetLocateActionTest {
    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The wire shape the client decodes: screenshot dimensions plus a map of resource path to
     * matches (scored, with the closest-rejected diagnostic), surviving the JSON round-trip.
     */
    @Test
    fun locateResultRoundTripsThroughJson() {
        val result = TargetLocateResult(
            1920,
            1080,
            mapOf(
                ResourcePath.parse("many.png") to TargetCropMatches(
                    listOf(
                        TargetMatchRect(1, 2, 3, 4, 1.0, 1.0),
                        TargetMatchRect(20, 10, 3, 4, 0.875, 1.25)),
                    null),
                ResourcePath.parse("absent.png") to TargetCropMatches(
                    listOf(),
                    TargetMatchRect(40, 50, 3, 4, 0.625, 1.0))))

        val overTheWire = ExecutionValue.fromJsonCollection(
            result.asExecutionValue().toJsonCollection())

        @Suppress("UNCHECKED_CAST")
        val roundTripped = TargetLocateResult.ofCollection(
            overTheWire.get() as Map<String, Any>)

        assertEquals(result, roundTripped)
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Overlapping matches (several crops finding the same spot, possibly at slightly different
     * offsets/scales) count as ONE target for the preview's uniqueness summary; disjoint
     * matches stay distinct. Transitive: a match bridging two clusters merges them.
     */
    @Test
    fun overlappingMatchesCountAsOneTarget() {
        assertEquals(0, TargetLocateResult.distinctTargetCount(listOf()))

        // The user-observed shape: three crops agree on the sidebar icon within a few pixels
        val agreeing = listOf(
            TargetMatchRect(75, 612, 21, 17, 1.0, 1.0),
            TargetMatchRect(78, 618, 23, 19, 0.98, 1.1),
            TargetMatchRect(78, 619, 32, 26, 0.84, 1.5))
        assertEquals(1, TargetLocateResult.distinctTargetCount(agreeing))

        val elsewhere = TargetMatchRect(400, 100, 21, 17, 1.0, 1.0)
        assertEquals(2, TargetLocateResult.distinctTargetCount(agreeing + elsewhere))

        // Edge-adjacent (touching, not overlapping) rectangles are distinct
        assertEquals(2, TargetLocateResult.distinctTargetCount(listOf(
            TargetMatchRect(0, 0, 10, 10, 1.0, 1.0),
            TargetMatchRect(10, 0, 10, 10, 1.0, 1.0))))

        // A wide match bridging two otherwise-disjoint matches merges all three
        assertEquals(1, TargetLocateResult.distinctTargetCount(listOf(
            TargetMatchRect(0, 0, 10, 10, 1.0, 1.0),
            TargetMatchRect(30, 0, 10, 10, 1.0, 1.0),
            TargetMatchRect(5, 5, 30, 10, 1.0, 1.0))))
    }
}
