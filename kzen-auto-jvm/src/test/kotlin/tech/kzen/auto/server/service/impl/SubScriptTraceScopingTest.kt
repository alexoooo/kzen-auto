package tech.kzen.auto.server.service.impl

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertEquals
import kotlin.test.fail


/**
 * Regression: a sub-Script hosted more than once in a run (here a [RunStep][tech.kzen.auto.server.objects.script.step.control.RunStep]
 * inside a ForEach body, invoked once per loop item) must get a FRESH trace scope on each re-entry — the prior
 * invocation's per-step values must not ghost into the next.
 *
 * Each host() is a distinct engine node, so each sub-Script invocation is its own execution, resolved in
 * isolation by the trace query view ([tech.kzen.auto.server.exec.RunEngineLogicTrace]) keyed on node id. The
 * anti-ghost on re-entry is the engine's own doing: the loop's per-iteration reset (dropReplay →
 * resetEmitted) clears the superseded invocation's live values, so exactly one invocation still carries the
 * child's step values.
 */
class SubScriptTraceScopingTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val rootPath = DocumentPath.parse("test/script/engine/script-engine-run-loop-test.yaml")
    private val childPath = DocumentPath.parse("test/script/navigation/step-nav-child-test.yaml")

    private val mainLocation = ObjectLocation(rootPath, ObjectPath.parse("main"))
    private val callLocation = ObjectLocation(rootPath, ObjectPath.parse("main.steps/Loop.steps/Call"))
    private val childBLocation = ObjectLocation(childPath, ObjectPath.parse("main.steps/ChildB"))

    private lateinit var context: KzenAutoContext


    //-----------------------------------------------------------------------------------------------------------------
    @Before
    fun setUp() {
        context = KzenAutoContext.forTest()
    }


    @After
    fun tearDown() {
        context.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun subScriptReEntryGetsAFreshTraceBufferPerInvocation() {
        val controller = context.serverLogicController
        val runId = controller.start(mainLocation, snapshot)
            ?: fail("Unable to start run")

        controller.continueOrStart(runId, snapshot)
        awaitDone()

        // ForEach 1..2 hosts the sub-Script twice; each invocation is its own child node, hence its own trace
        // execution buffer (parent = the root execution), NOT flattened into one shared buffer.
        val executions = context.logicTrace.lookupRunExecutions(runId)
        val childExecutions = executions.filter { it.parentExecutionId != null }
        assertEquals(
            2, childExecutions.size,
            "each sub-Script invocation must get its own trace buffer (re-entry, not a shared/flattened buffer)")

        // Each invocation is attributed to its hosting call-site (the RunStep), so a consumer (the RunStep
        // screenshot strip) can scope its view to exactly the executions this step spawned.
        val callStableId = context.objectStableMapper.objectStableId(callLocation)
        assertEquals(
            listOf(callStableId, callStableId),
            childExecutions.map { it.callerStableId },
            "each sub-Script invocation must be attributed to the hosting RunStep call-site")

        // Anti-ghost: opening the 2nd invocation's buffer clears the 1st's live values, so exactly ONE
        // invocation (the last / live one) still carries the child's step values — the earlier one does not
        // ghost its finished ChildB into the next re-entry.
        val childBPath = LogicTracePath.ofObjectStableId(
            context.objectStableMapper.objectStableId(childBLocation))
        val invocationsCarryingChildB = childExecutions.count { info ->
            val invocationSnapshot = context.logicTrace.lookup(
                LogicRunExecutionId(runId, info.executionId), LogicTraceQuery(LogicTracePath.root))
            invocationSnapshot?.values?.containsKey(childBPath) == true
        }
        assertEquals(
            1, invocationsCarryingChildB,
            "a prior sub-Script invocation's step values must be cleared on re-entry (no ghosting)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val snapshot: GraphDefinitionAttempt
        get() = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())


    private fun awaitDone() {
        for (attempt in 0 until 500) {
            if (context.serverLogicController.status().active == null) {
                return
            }
            Thread.sleep(10)
        }
        fail("Run did not complete")
    }
}
