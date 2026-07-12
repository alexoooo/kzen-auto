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
}
