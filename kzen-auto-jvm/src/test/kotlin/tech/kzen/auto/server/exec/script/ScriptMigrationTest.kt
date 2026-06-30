package tech.kzen.auto.server.exec.script

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.exec.script.step.ForEachStep
import tech.kzen.auto.server.exec.script.step.FormulaStep
import tech.kzen.auto.server.exec.script.step.ResultStep
import tech.kzen.auto.server.exec.script.step.SequenceStep
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Node
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.server.exec.engine.RunEngine
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


/**
 * Engine-side coverage of the Script flavour's live-edit state migration (logic-spec §5): pause -> edit the
 * Script -> resume, driven directly through [RunEngine.migrate]. The clean-room successor to the old
 * `StatefulLogicElement`/`ActiveScriptModel` reload: the run's completed work ([ScriptMigrationState]) is captured
 * at the quiescent barrier and re-adopted on the rebuilt run, so the [SequenceStep] spine replay-short-circuits
 * completed steps (resume continues from the live frontier) instead of restarting from the top.
 *
 * Both tests build a SECOND [ScriptLogic] from the same factory and hand it to [RunEngine.migrate] at a paused
 * frontier — exactly what the controller's edit-detection does on resume-against-an-edited-snapshot. Counters
 * (shared across the base + rebuilt logic via the factory closure) make "ran exactly once" observable, so a
 * spurious re-run of a completed step is caught, not just a wrong final value.
 */
class ScriptMigrationTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val rootId = ObjectStableId("script")

    private fun id(value: String) = ObjectStableId(value)


    // The display text a step recorded (its value's text rendering), or null if it has not produced one yet.
    private fun stepDisplay(node: Node, name: String): Any? {
        val value = node.live[Address.of(name)]
            ?: return null
        return StepTrace.ofExecutionValue(value).displayValue.get()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun editingALaterStepWhilePausedResumesFromTheCompletedPrefix() {
        // a -> b -> r. Pause (via step) after `a` completes and before `b` runs, edit `b`'s value, then migrate +
        // resume. `a` is the completed prefix: it must NOT re-run (its counter stays 1) and its value is carried.
        // `b` is the live frontier: it runs against the EDITED value, so the result reflects the edit. A clean
        // restart would re-run `a` (counter 2) and a stale carry would ignore the edit — only a correct
        // replay-short-circuit hits both numbers.
        val aRuns = AtomicInteger()
        val bRuns = AtomicInteger()

        fun scriptFor(bValue: Long) = ScriptLogic(SequenceStep(listOf(
            FormulaStep(id("a")) { aRuns.incrementAndGet(); 1L },
            FormulaStep(id("b")) { bRuns.incrementAndGet(); bValue },
            ResultStep(id("r")) {
                (it.referencedValue(id("a")) as Long) + (it.referencedValue(id("b")) as Long)
            }
        )))

        RunEngine(scriptFor(10L), rootId).use { engine ->
            // Step to the frontier between `a` (done) and `b` (next, not yet run).
            engine.step()
            engine.awaitQuiescent()
            engine.step()
            engine.awaitQuiescent()
            assertEquals("1", stepDisplay(engine.snapshot().root, "a"))
            assertEquals(1, aRuns.get(), "a ran once before the edit")
            assertEquals(0, bRuns.get(), "b had not run yet at the pause")

            // Resume against the edited definition (b: 10 -> 20): the controller's migrate at the quiescent barrier.
            engine.migrate(scriptFor(20L), paused = false)
            val outcome = runBlocking { engine.await() }

            val success = assertIs<Outcome.Success>(outcome)
            assertEquals(
                21L, success.value.mainComponentValue(),
                "completed `a` (=1) carried and not re-run, edited `b` (=20) ran live -> 1 + 20")
            assertEquals(1, aRuns.get(), "the completed prefix step `a` was re-adopted, not re-executed")
            assertEquals(1, bRuns.get(), "the frontier step `b` ran exactly once, live, post-migrate")
        }
    }


    @Test
    fun migrationMidLoopRestartsLoopWithCorrectValuesAndKeepsThePrefix() {
        // items -> forEach{ times10 } -> r. Pause mid-loop (after the first body iteration), then migrate + resume.
        // The pre-loop `items` step is the completed prefix: re-adopted, NOT re-run (counter stays 1). The loop had
        // not completed, so it restarts from its first iteration — its body's stale per-iteration outcome must be
        // dropped so the collected output is the exact [10, 20, 30] (a stale carry would repeat the first value; a
        // double count would exceed three elements). bodyRuns ends at 4 (1 before + 3 after) — the documented
        // restart-from-zero cost of the coroutine model (no mid-loop resume).
        val itemsRuns = AtomicInteger()
        val bodyRuns = AtomicInteger()

        fun scriptFor() = ScriptLogic(SequenceStep(listOf(
            FormulaStep(id("items")) { itemsRuns.incrementAndGet(); listOf(1L, 2L, 3L) },
            ForEachStep(id("forEach"), id("items"), id("item"), body = SequenceStep(listOf(
                FormulaStep(id("times10")) { bodyRuns.incrementAndGet(); (it.referencedValue(id("item")) as Long) * 10L }
            ))),
            ResultStep(id("r")) { it.referencedValue(id("forEach")) }
        )))

        RunEngine(scriptFor(), rootId).use { engine ->
            // Step to mid-loop: items done, forEach started, exactly the first body iteration completed.
            var guard = 0
            while (bodyRuns.get() < 1 && guard < 50) {
                engine.step()
                engine.awaitQuiescent()
                guard += 1
            }
            assertEquals(1, bodyRuns.get(), "exactly one body iteration ran before the edit")
            assertEquals(1, itemsRuns.get(), "items ran once")
            assertEquals("[1, 2, 3]", stepDisplay(engine.snapshot().root, "items"))

            engine.migrate(scriptFor(), paused = false)
            val outcome = runBlocking { engine.await() }

            val success = assertIs<Outcome.Success>(outcome)
            assertEquals(
                listOf(10L, 20L, 30L), success.value.mainComponentValue(),
                "the restarted loop collected each item exactly once -> no stale carry, no double count")
            assertEquals(1, itemsRuns.get(), "the completed pre-loop step was re-adopted, not re-executed")
            assertEquals(4, bodyRuns.get(), "the incomplete loop restarted from iteration 0 (1 before + 3 after)")
        }
    }
}
