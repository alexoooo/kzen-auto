package tech.kzen.auto.server.objects.logic

import org.junit.Test
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.server.exec.logic.trace.LogicTraceStore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * Execution-tree recording in LogicTraceStore: each opened execution gets a row carrying its parent
 * execution and its call-site, so two RunSteps invoking the SAME sub-script document are distinguished
 * (the bleed-through the RunStep screenshot strip relies on this to fix).
 */
class LogicTraceStoreExecutionTreeTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val rootExecution = LogicRunExecutionId.random()
    private val runId = rootExecution.logicRunId

    private val rootLocation = objectLocation("main.yaml", "MyScript")
    private val runStepA = objectLocation("main.yaml", "RunA")
    private val runStepB = objectLocation("main.yaml", "RunB")
    private val subScript = objectLocation("sub.yaml", "Sub")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `sibling invocations of the same sub-script get distinct rows sharing a parent`() {
        val mapper = ObjectStableMapper()
        val store = LogicTraceStore(mapper)

        // Root execution: no parent, no caller.
        store.handle(rootExecution, rootLocation, null, null)

        // Two different RunSteps in the root invoke the SAME sub-script document, each as its own child
        // execution under the root.
        val childA = LogicRunExecutionId(runId, LogicExecutionId.random())
        val childB = LogicRunExecutionId(runId, LogicExecutionId.random())
        store.handle(childA, subScript, rootExecution.logicExecutionId, runStepA)
        store.handle(childB, subScript, rootExecution.logicExecutionId, runStepB)

        val executions = store.lookupRunExecutions(runId)
        assertEquals(3, executions.size)

        val rootRow = executions.single { it.executionId == rootExecution.logicExecutionId }
        assertNull(rootRow.parentExecutionId)
        assertNull(rootRow.callerStableId)

        val rowA = executions.single { it.executionId == childA.logicExecutionId }
        val rowB = executions.single { it.executionId == childB.logicExecutionId }

        // Same parent (the root invocation) ...
        assertEquals(rootExecution.logicExecutionId, rowA.parentExecutionId)
        assertEquals(rootExecution.logicExecutionId, rowB.parentExecutionId)

        // ... but distinct call-sites, so the two invocations of the same document can be told apart.
        assertEquals(mapper.objectStableId(runStepA), rowA.callerStableId)
        assertEquals(mapper.objectStableId(runStepB), rowB.callerStableId)
        assertTrue(rowA.callerStableId != rowB.callerStableId)
    }


    @Test
    fun `lookupRunExecutions returns only the requested run`() {
        val mapper = ObjectStableMapper()
        val store = LogicTraceStore(mapper)

        // Run 1: a root plus one child invocation.
        store.handle(rootExecution, rootLocation, null, null)
        val child = LogicRunExecutionId(runId, LogicExecutionId.random())
        store.handle(child, subScript, rootExecution.logicExecutionId, runStepA)

        // Run 2 on a DIFFERENT root document, so it coexists rather than evicting run 1 (a re-run of the
        // SAME root would evict the prior run — see LogicTraceStoreRenameTest).
        val otherRun = LogicRunExecutionId.random()
        val otherRoot = objectLocation("other.yaml", "OtherScript")
        store.handle(otherRun, otherRoot, null, null)

        val executions = store.lookupRunExecutions(runId)
        assertEquals(
            setOf(rootExecution.logicExecutionId, child.logicExecutionId),
            executions.map { it.executionId }.toSet())

        val otherExecutions = store.lookupRunExecutions(otherRun.logicRunId)
        assertNotNull(otherExecutions.singleOrNull())
        assertEquals(otherRun.logicExecutionId, otherExecutions.single().executionId)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun objectLocation(documentPath: String, objectName: String): ObjectLocation {
        return ObjectLocation(
            DocumentPath.parse(documentPath),
            ObjectPath.parse(objectName))
    }
}
