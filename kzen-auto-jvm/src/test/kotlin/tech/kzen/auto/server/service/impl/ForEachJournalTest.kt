package tech.kzen.auto.server.service.impl

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.script.model.ForEachProgress
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.auto.server.util.awaitDone
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceRange
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail


/**
 * A ForEach loop's journal read by range through [tech.kzen.lib.common.exec.logic.trace.LogicTrace.lookupRange],
 * after the run has settled: nested loops (the inner journal resets with the inner loop's trace at each outer
 * iteration), a loop in a hosted sub-Script (read with or without its execution), and a part of the list.
 * Paused, live-edit and move-to reads are covered beside the tests of those features
 * (ServerLogicControllerLoopMigrationTest, ScriptMoveToTest).
 */
class ForEachJournalTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val nestedPath = DocumentPath.parse("test/script/loop/script-loop-collection-nested-test.yaml")
    private val hostPath = DocumentPath.parse("test/script/loop/script-loop-journal-test.yaml")
    private val childPath = DocumentPath.parse("test/script/loop/script-loop-journal-child-test.yaml")

    private lateinit var context: KzenAutoContext


    //-----------------------------------------------------------------------------------------------------------------
    @Before
    fun setUp() {
        context = KzenAutoContext.forTest()
    }


    @After
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun anInnerLoopsJournalResetsAtEachOuterIteration() {
        val runId = run(nestedPath)

        assertEquals(
            listOf("1" to "[1, 2]", "2" to "[2, 4]"),
            entries(range(runId, null, nestedPath, "main.steps/Outer")))
        assertEquals(
            listOf("1" to "2", "2" to "4"),
            entries(range(runId, null, nestedPath, "main.steps/Outer.steps/Inner")),
            "the last outer pass's inner journal only: the earlier pass was reset with the inner loop's trace")
    }


    @Test
    fun aLoopInASubScriptIsReadWithOrWithoutItsExecution() {
        val runId = run(hostPath)
        val expected = listOf("1" to "10", "2" to "20", "3" to "30")

        assertEquals(expected, entries(range(runId, null, childPath, "main.steps/Loop")), "the run's latest list")

        val executions = context.logicTrace.lookupRunExecutions(runId)
        val hostStableId = context.objectStableMapper.objectStableId(
            ObjectLocation(hostPath, ObjectPath.parse("main.steps/Run")))
        val child = executions.single { it.callerStableId == hostStableId }.executionId
        val root = executions.single { it.parentExecutionId == null }.executionId

        assertEquals(expected, entries(range(runId, child, childPath, "main.steps/Loop")), "the child's own list")
        assertNull(
            context.logicTrace.lookupRange(runId, root, pathOf(childPath, "main.steps/Loop"), 0, null),
            "the list is the hosted child's, not the host's")
    }


    @Test
    fun aRangeIsPartOfTheList() {
        val runId = run(hostPath)
        val path = pathOf(childPath, "main.steps/Loop")

        val middle = context.logicTrace.lookupRange(runId, null, path, 1, 1)
            ?: fail("No journal")
        assertEquals(3, middle.size, "the whole list's size")
        assertEquals(1, middle.offset)
        assertEquals(listOf("2" to "20"), entries(middle))

        val past = context.logicTrace.lookupRange(runId, null, path, 5, 10)
            ?: fail("No journal")
        assertTrue(past.entries.isEmpty(), "a range past the end is empty")
        assertEquals(3, past.offset, "and starts at the end")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun run(documentPath: DocumentPath): LogicRunId {
        val controller = context.serverLogicController
        val definition = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())
        val runId = controller.start(ObjectLocation(documentPath, ObjectPath.parse("main")), definition)
            ?: fail("Unable to start run")
        controller.continueOrStart(runId, definition)
        controller.awaitDone()
        return runId
    }


    private fun pathOf(documentPath: DocumentPath, objectPath: String): LogicTracePath {
        return LogicTracePath.ofObjectStableId(context.objectStableMapper.objectStableId(
            ObjectLocation(documentPath, ObjectPath.parse(objectPath))))
    }


    private fun range(
        runId: LogicRunId,
        executionId: LogicExecutionId?,
        documentPath: DocumentPath,
        objectPath: String
    ): LogicTraceRange {
        return context.logicTrace.lookupRange(runId, executionId, pathOf(documentPath, objectPath), 0, null)
            ?: fail("No journal for: $objectPath")
    }


    private fun entries(range: LogicTraceRange): List<Pair<String, String>> {
        return range.entries.map {
            val entry = ForEachProgress.entryOfExecutionValueOrNull(it)
                ?: fail("Not a journal entry: $it")
            entry.item to entry.value
        }
    }
}
