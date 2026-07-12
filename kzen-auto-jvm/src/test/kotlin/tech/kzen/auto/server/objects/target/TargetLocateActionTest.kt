package tech.kzen.auto.server.objects.target

import org.junit.Test
import tech.kzen.auto.common.objects.document.target.TargetLocateResult
import tech.kzen.auto.common.objects.document.target.TargetMatchRect
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import kotlin.test.assertEquals


class TargetLocateActionTest {
    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The wire shape the client decodes: screenshot dimensions plus a map of resource path to
     * list of match rectangles, surviving the JSON round-trip.
     */
    @Test
    fun locateResultRoundTripsThroughJson() {
        val result = TargetLocateResult(
            1920,
            1080,
            mapOf(
                ResourcePath.parse("many.png") to listOf(
                    TargetMatchRect(1, 2, 3, 4),
                    TargetMatchRect(20, 10, 3, 4)),
                ResourcePath.parse("absent.png") to listOf()))

        val overTheWire = ExecutionValue.fromJsonCollection(
            result.asExecutionValue().toJsonCollection())

        @Suppress("UNCHECKED_CAST")
        val roundTripped = TargetLocateResult.ofCollection(
            overTheWire.get() as Map<String, Any>)

        assertEquals(result, roundTripped)
    }
}
