package tech.kzen.auto.server.objects.logic

import org.junit.After
import org.junit.Test
import tech.kzen.auto.common.paradigm.logic.run.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.logic.run.model.LogicRunExecutionId
import tech.kzen.auto.common.paradigm.logic.run.model.LogicRunId
import tech.kzen.auto.common.paradigm.logic.trace.model.LogicTracePath
import tech.kzen.auto.common.paradigm.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.cqrs.DeletedDocumentEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedObjectEvent
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import kotlin.test.assertEquals
import kotlin.test.assertNull


/**
 * Move A canary — the trace stays resolvable when a rename event arrives at the
 * process-global mapper after the run has ended. Pre-Move A the per-run mapper
 * was unobserved on clearState, so post-stop renames went untracked and lookups
 * translated against a stale id → location map.
 */
class LogicTraceStoreRenameTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val testRunId = LogicRunId("test-run-${System.nanoTime()}")
    private val runExecutionId = LogicRunExecutionId(
        testRunId, LogicExecutionId(testRunId.value))
    private val rootLocation = objectLocation("a.yaml", "MyScript")
    private val stepLocation = objectLocation("a.yaml", "Step1")


    @After
    fun cleanup() {
        LogicTraceStore.evict(testRunId)
        LogicTraceStore.clear(rootLocation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `trace lookup resolves under new name after rename event arrives post-stop`() {
        val mapper = ObjectStableMapper()

        // Simulate boot pre-warm
        mapper.objectStableId(rootLocation)
        val originalStableId = mapper.objectStableId(stepLocation)

        // Active run writes a trace entry under the step's stable id
        val handle = LogicTraceStore.handle(runExecutionId, rootLocation, mapper)
        handle.set(
            LogicTracePath.ofObjectStableId(originalStableId),
            ExecutionValue.of("done"))

        // Run terminates — buffer stays in LogicTraceStore.history;
        // mapper continues to observe (no unobserve in the Move-A model)

        // Rename event arrives at the global mapper AFTER the run ended
        mapper.apply(RenamedObjectEvent(stepLocation, ObjectName("Step1Renamed")))

        // Lookup should resolve under the new name
        val renamedLocation = objectLocation("a.yaml", "Step1Renamed")
        val expectedPath = LogicTracePath.ofObjectLocation(renamedLocation)

        val snapshot = LogicTraceStore.lookup(runExecutionId, LogicTraceQuery(LogicTracePath.root))
        checkNotNull(snapshot)
        assertEquals(ExecutionValue.of("done"), snapshot.values[expectedPath])

        // And no entry under the old name
        val originalPath = LogicTracePath.ofObjectLocation(stepLocation)
        assertNull(snapshot.values[originalPath])
    }


    @Test
    fun `trace lookup follows a chain of renames after run-stop`() {
        val mapper = ObjectStableMapper()
        mapper.objectStableId(rootLocation)
        val originalStableId = mapper.objectStableId(stepLocation)

        val handle = LogicTraceStore.handle(runExecutionId, rootLocation, mapper)
        handle.set(
            LogicTracePath.ofObjectStableId(originalStableId),
            ExecutionValue.of("done"))

        // A -> B -> C
        mapper.apply(RenamedObjectEvent(stepLocation, ObjectName("Mid")))
        mapper.apply(RenamedObjectEvent(objectLocation("a.yaml", "Mid"), ObjectName("Final")))

        val finalLocation = objectLocation("a.yaml", "Final")
        val expectedPath = LogicTracePath.ofObjectLocation(finalLocation)

        val snapshot = LogicTraceStore.lookup(runExecutionId, LogicTraceQuery(LogicTracePath.root))
        checkNotNull(snapshot)
        assertEquals(ExecutionValue.of("done"), snapshot.values[expectedPath])
    }


    @Test
    fun `trace entry drops when its object is deleted from the graph`() {
        val mapper = ObjectStableMapper()
        mapper.objectStableId(rootLocation)
        val originalStableId = mapper.objectStableId(stepLocation)

        val handle = LogicTraceStore.handle(runExecutionId, rootLocation, mapper)
        handle.set(
            LogicTracePath.ofObjectStableId(originalStableId),
            ExecutionValue.of("done"))

        // Whole document deleted — the step's id becomes unresolvable
        mapper.apply(DeletedDocumentEvent(DocumentPath.parse("a.yaml")))

        val snapshot = LogicTraceStore.lookup(runExecutionId, LogicTraceQuery(LogicTracePath.root))
        checkNotNull(snapshot)
        assertEquals(emptyMap(), snapshot.values)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun objectLocation(documentPath: String, objectName: String): ObjectLocation {
        return ObjectLocation(
            DocumentPath.parse(documentPath),
            ObjectPath.parse(objectName))
    }
}
