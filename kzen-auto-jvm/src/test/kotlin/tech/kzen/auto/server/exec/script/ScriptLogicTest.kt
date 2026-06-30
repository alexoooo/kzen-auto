package tech.kzen.auto.server.exec.script

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.server.exec.script.step.ForEachStep
import tech.kzen.auto.server.exec.script.step.FormulaStep
import tech.kzen.auto.server.exec.script.step.IfStep
import tech.kzen.auto.server.exec.script.step.PauseStep
import tech.kzen.auto.server.exec.script.step.ResultStep
import tech.kzen.auto.server.exec.script.step.RunStep
import tech.kzen.auto.server.exec.script.step.SequenceStep
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Node
import tech.kzen.lib.common.exec.engine.NodeStatus
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.engine.PauseReason
import tech.kzen.lib.server.exec.engine.RunEngine
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * Drives the clean-room Script flavour through the new [RunEngine], proving Script's full control vocabulary
 * (sequence, if, foreach, nested run, pause) and result contract are expressible on the ~12-concept surface
 * with no engine changes — no StatefulLogicElement, no pollCommand, no budget math.
 *
 * A step's live value is a [StepTrace] (state + display) keyed by its stable id — the trace shape the client
 * consumes — so step values are read here via [stepDisplay]. The "next to run" highlight is a reserved-address
 * entry ([ScriptRunContext.nextStepAddressMarker]), read via [nextStep].
 */
class ScriptLogicTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val rootId = ObjectStableId("script")

    private fun id(value: String) = ObjectStableId(value)


    // The display text a step recorded (the value's text rendering), or null if it hasn't produced one yet.
    private fun stepDisplay(node: Node, name: String): Any? {
        val value = node.live[Address.of(name)]
            ?: return null
        return StepTrace.ofExecutionValue(value).displayValue.get()
    }


    // The stable id of the step currently highlighted as "next to run" (null when cleared).
    private fun nextStep(node: Node): Any? {
        return node.live[Address.of(ScriptRunContext.nextStepAddressMarker)]?.get()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun sequentialFormulasProduceResult() = runBlocking {
        val a = id("a")
        val b = id("b")
        val r = id("r")
        val script = ScriptLogic(SequenceStep(listOf(
            FormulaStep(a) { 2L },
            FormulaStep(b) { 3L },
            ResultStep(r) { (it.referencedValue(a) as Long) + (it.referencedValue(b) as Long) }
        )))

        val engine = RunEngine(script, rootId)
        try {
            engine.resume()
            val outcome = engine.await()

            val success = assertIs<Outcome.Success>(outcome)
            assertEquals(5L, success.value.mainComponentValue())

            val root = engine.snapshot().root
            assertEquals("2", stepDisplay(root, "a"))
            assertEquals("3", stepDisplay(root, "b"))
            assertEquals("5", stepDisplay(root, "r"))
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun ifSelectsBranchByCondition() = runBlocking {
        fun scriptFor(condition: Boolean): ScriptLogic {
            val cond = id("cond")
            return ScriptLogic(SequenceStep(listOf(
                FormulaStep(cond) { condition },
                IfStep(
                    id("if"),
                    cond,
                    thenBranch = SequenceStep(listOf(ResultStep(id("thenResult")) { 10L })),
                    elseBranch = SequenceStep(listOf(ResultStep(id("elseResult")) { 20L }))
                )
            )))
        }

        RunEngine(scriptFor(true), rootId).use { engine ->
            engine.resume()
            assertEquals(10L, assertIs<Outcome.Success>(engine.await()).value.mainComponentValue())
        }

        RunEngine(scriptFor(false), rootId).use { engine ->
            engine.resume()
            assertEquals(20L, assertIs<Outcome.Success>(engine.await()).value.mainComponentValue())
        }
    }


    @Test
    fun forEachCollectsBodyValues() = runBlocking {
        val items = id("items")
        val item = id("item")
        val forEach = id("forEach")
        val result = id("result")
        val script = ScriptLogic(SequenceStep(listOf(
            FormulaStep(items) { listOf(1L, 2L, 3L) },
            ForEachStep(forEach, items, item, body = SequenceStep(listOf(
                FormulaStep(id("times10")) { (it.referencedValue(item) as Long) * 10L }
            ))),
            ResultStep(result) { it.referencedValue(forEach) }
        )))

        RunEngine(script, rootId).use { engine ->
            engine.resume()
            val success = assertIs<Outcome.Success>(engine.await())
            assertEquals(listOf(10L, 20L, 30L), success.value.mainComponentValue())
            assertEquals(
                listOf(10L, 20L, 30L).toString(),
                stepDisplay(engine.snapshot().root, "forEach"))
        }
    }


    @Test
    fun runStepHostsChildScriptAsConfinedNode() = runBlocking {
        val child = ScriptLogic(SequenceStep(listOf(
            ResultStep(id("childResult")) { 42L }
        )))
        val run = id("run")
        val result = id("result")
        val childNodeId = id("child")
        val parent = ScriptLogic(SequenceStep(listOf(
            RunStep(run, childNodeId, child),
            ResultStep(result) { it.referencedValue(run) }
        )))

        RunEngine(parent, rootId).use { engine ->
            engine.resume()
            val success = assertIs<Outcome.Success>(engine.await())
            assertEquals(42L, success.value.mainComponentValue())

            val childNode = engine.snapshot().root.children.single()
            assertEquals(childNodeId, childNode.stableId)
            assertEquals("42", stepDisplay(childNode, "childResult"))
        }
    }


    @Test
    fun pauseStepSuspendsUntilResumed() = runBlocking {
        val before = id("before")
        val result = id("result")
        val script = ScriptLogic(SequenceStep(listOf(
            FormulaStep(before) { 1L },
            PauseStep(id("pause")),
            ResultStep(result) { 2L }
        )))

        RunEngine(script, rootId).use { engine ->
            engine.resume()
            engine.awaitQuiescent()

            val status = engine.snapshot().root.status
            assertIs<NodeStatus.Suspended>(status)
            assertEquals(PauseReason.Explicit, status.reason)
            assertEquals("1", stepDisplay(engine.snapshot().root, "before"))

            engine.resume()
            assertEquals(2L, assertIs<Outcome.Success>(engine.await()).value.mainComponentValue())
        }
    }


    @Test
    fun stepAdvancesOneStepAtATime() = runBlocking {
        val a = id("a")
        val b = id("b")
        val c = id("c")
        val script = ScriptLogic(SequenceStep(listOf(
            FormulaStep(a) { 1L },
            FormulaStep(b) { 2L },
            FormulaStep(c) { 3L },
            ResultStep(id("r")) { it.referencedValue(c) }
        )))

        RunEngine(script, rootId).use { engine ->
            engine.step()
            engine.awaitQuiescent()
            // Settled before the first step: it is highlighted as next, but has produced no value yet.
            assertIs<NodeStatus.Suspended>(engine.snapshot().root.status)
            assertEquals("a", nextStep(engine.snapshot().root))
            assertNull(stepDisplay(engine.snapshot().root, "a"))

            engine.step()
            engine.awaitQuiescent()
            assertEquals("1", stepDisplay(engine.snapshot().root, "a"))
            assertNull(stepDisplay(engine.snapshot().root, "b"))
            assertEquals("b", nextStep(engine.snapshot().root))

            engine.step()
            engine.awaitQuiescent()
            assertEquals("2", stepDisplay(engine.snapshot().root, "b"))

            engine.resume()
            assertEquals(3L, assertIs<Outcome.Success>(engine.await()).value.mainComponentValue())
        }
    }


    @Test
    fun cancelDuringPauseSettlesCancelled() = runBlocking {
        val script = ScriptLogic(SequenceStep(listOf(
            FormulaStep(id("a")) { 1L },
            PauseStep(id("pause")),
            ResultStep(id("r")) { 2L }
        )))

        RunEngine(script, rootId).use { engine ->
            engine.resume()
            engine.awaitQuiescent()
            assertIs<NodeStatus.Suspended>(engine.snapshot().root.status)

            engine.cancel()
            engine.awaitQuiescent()

            assertEquals(Outcome.Cancelled, engine.await())
            assertTrue(engine.snapshot().root.status is NodeStatus.Terminal)
        }
    }
}
