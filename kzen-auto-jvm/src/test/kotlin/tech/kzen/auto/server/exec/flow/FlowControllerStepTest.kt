package tech.kzen.auto.server.exec.flow

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.lib.common.exec.logic.run.model.LogicRunState
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail


/**
 * Regression: stepping a Flow through [ServerLogicController][tech.kzen.auto.server.service.impl.ServerLogicController]
 * via the exact client control path must ADVANCE to completion.
 *
 * The client's Step (and Run) do NOT pass a graph-definition snapshot — the controller re-fetches it from the
 * graph store each tick. A [tech.kzen.lib.common.model.definition.GraphDefinitionAttempt] is rebuilt fresh on
 * every fetch, and a Flow vertex definition embeds freshly-constructed mutable channel scaffolding
 * (MutableFlowOutput / MutableRequiredInput) with identity equality — so two builds of the SAME notation are
 * never definition-equal. The controller's live-edit change-detection therefore mistook every no-edit step for
 * an edit and migrated (re-parking at the same wavefront) instead of stepping, so the run never advanced. The
 * fix keys change-detection off the deterministic notation, not the compiled definition; this pins it.
 *
 * The other controller step / migration tests all pass a FIXED snapshot instance (whose filtered definition
 * reuses the same ObjectDefinition instances, so identity comparison happened to hold) — so none of them
 * exercised the client's null-snapshot re-fetch path this test drives.
 */
class FlowControllerStepTest {
    private lateinit var context: KzenAutoContext

    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    @Test
    fun steppingStreamFlowViaClientNullSnapshotPathCompletes() {
        context = KzenAutoContext.forTest()
        val controller = context.serverLogicController
        val flowMain = ObjectLocation(
            DocumentPath.parse("test/flow/flow-stream-test.yaml"), ObjectPath.parse("main"))

        // Start with the graph-store snapshot (as logicStart does), then step with NO snapshot (as logicStep
        // does) — the controller re-fetches a fresh definition each tick.
        val startSnapshot = runBlocking { context.graphStore.graphDefinition() }
        val runId = controller.start(flowMain, startSnapshot)
            ?: fail("Unable to start run")

        controller.startStep(runId)
        awaitSettled()

        var finished = false
        for (tick in 0 until 30) {
            if (controller.status().active == null) {
                finished = true
                break
            }
            controller.step(runId)   // null snapshot — the client path that re-fetches the definition
            awaitSettled()
        }

        assertTrue(
            finished,
            "stream Flow stepping via the client null-snapshot path stalled instead of advancing to completion")
    }


    // Poll until the in-flight step has settled (back to a Paused-family state, or the run finished).
    private fun awaitSettled() {
        for (attempt in 0 until 500) {
            val state = context.serverLogicController.status().active?.state
            if (state == null || (state != LogicRunState.Stepping && state != LogicRunState.Running)) {
                return
            }
            Thread.sleep(10)
        }
        fail("Step did not settle")
    }
}
