package tech.kzen.auto.common.paradigm.flow

import tech.kzen.auto.common.paradigm.flow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualVertexPhase
import kotlin.test.Test
import kotlin.test.assertEquals


/**
 * Pins [VisualVertexModel.phase]'s precedence, in particular that a set error outranks every settled phase but
 * not the live Running one (a re-running vertex whose previous failure hasn't been cleared yet renders as
 * running, not as an error).
 */
class VisualVertexModelPhaseTest {
    //-----------------------------------------------------------------------------------------------------------------
    private fun model(
        running: Boolean = false,
        hasNext: Boolean = false,
        epoch: Int = 0,
        error: String? = null
    ): VisualVertexModel {
        return VisualVertexModel(running, null, null, hasNext, epoch, error)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun runningOutranksError() {
        assertEquals(
            VisualVertexPhase.Running,
            model(running = true, error = "boom").phase())
    }


    @Test
    fun errorOnPendingVertex() {
        assertEquals(VisualVertexPhase.Error, model(epoch = 0, error = "boom").phase())
    }


    @Test
    fun errorOutranksRemaining() {
        assertEquals(
            VisualVertexPhase.Error,
            model(epoch = 1, hasNext = true, error = "boom").phase())
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun pendingWithoutError() {
        assertEquals(VisualVertexPhase.Pending, model(epoch = 0).phase())
    }


    @Test
    fun remainingWithoutError() {
        assertEquals(VisualVertexPhase.Remaining, model(epoch = 1, hasNext = true).phase())
    }


    @Test
    fun doneWithoutError() {
        assertEquals(VisualVertexPhase.Done, model(epoch = 1).phase())
    }
}
