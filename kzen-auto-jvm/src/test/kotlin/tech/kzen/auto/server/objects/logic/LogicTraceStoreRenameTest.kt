package tech.kzen.auto.server.objects.logic

import org.junit.Test
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.cqrs.DeletedDocumentEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedObjectEvent
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.server.exec.logic.trace.LogicTraceStore
import kotlin.test.assertEquals
import kotlin.test.assertNull


/**
 * Move A canary — the trace stays resolvable when a rename event arrives at the
 * process-global mapper after the run has ended. Pre-Move A the per-run mapper
 * was unobserved on clearState, so post-stop renames went untracked and lookups
 * translated against a stale id → location map.
 *
 * Post-Move B: LogicTraceStore is a constructor-injected instance rather than a
 * singleton, so each test owns its own store — no shared @After cleanup needed.
 */
class LogicTraceStoreRenameTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val testRunId = LogicRunId("test-run-${System.nanoTime()}")
    private val runExecutionId = LogicRunExecutionId(
        testRunId, LogicExecutionId(testRunId.value))
    private val rootLocation = objectLocation("a.yaml", "MyScript")
    private val stepLocation = objectLocation("a.yaml", "Step1")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `trace lookup resolves under new name after rename event arrives post-stop`() {
        val mapper = ObjectStableMapper()
        val store = LogicTraceStore(mapper)

        // Simulate boot pre-warm
        mapper.objectStableId(rootLocation)
        val originalStableId = mapper.objectStableId(stepLocation)

        // Active run writes a trace entry under the step's stable id
        val handle = store.handle(runExecutionId, rootLocation)
        handle.set(
            LogicTracePath.ofObjectStableId(originalStableId),
            ExecutionValue.of("done"))

        // Run terminates — buffer stays in the store; mapper keeps observing
        // (no unobserve in the Move-A model)

        // Rename event arrives at the global mapper AFTER the run ended
        mapper.apply(RenamedObjectEvent(stepLocation, ObjectName("Step1Renamed")))

        // Lookup should resolve under the new name
        val renamedLocation = objectLocation("a.yaml", "Step1Renamed")
        val expectedPath = LogicTracePath.ofObjectLocation(renamedLocation)

        val snapshot = store.lookup(runExecutionId, LogicTraceQuery(LogicTracePath.root))
        checkNotNull(snapshot)
        assertEquals(ExecutionValue.of("done"), snapshot.values[expectedPath])

        // And no entry under the old name
        val originalPath = LogicTracePath.ofObjectLocation(stepLocation)
        assertNull(snapshot.values[originalPath])
    }


    @Test
    fun `trace lookup follows a chain of renames after run-stop`() {
        val mapper = ObjectStableMapper()
        val store = LogicTraceStore(mapper)
        mapper.objectStableId(rootLocation)
        val originalStableId = mapper.objectStableId(stepLocation)

        val handle = store.handle(runExecutionId, rootLocation)
        handle.set(
            LogicTracePath.ofObjectStableId(originalStableId),
            ExecutionValue.of("done"))

        // A -> B -> C
        mapper.apply(RenamedObjectEvent(stepLocation, ObjectName("Mid")))
        mapper.apply(RenamedObjectEvent(objectLocation("a.yaml", "Mid"), ObjectName("Final")))

        val finalLocation = objectLocation("a.yaml", "Final")
        val expectedPath = LogicTracePath.ofObjectLocation(finalLocation)

        val snapshot = store.lookup(runExecutionId, LogicTraceQuery(LogicTracePath.root))
        checkNotNull(snapshot)
        assertEquals(ExecutionValue.of("done"), snapshot.values[expectedPath])
    }


    @Test
    fun `trace entry drops when its object is deleted from the graph`() {
        val mapper = ObjectStableMapper()
        val store = LogicTraceStore(mapper)
        mapper.objectStableId(rootLocation)
        val originalStableId = mapper.objectStableId(stepLocation)

        val handle = store.handle(runExecutionId, rootLocation)
        handle.set(
            LogicTracePath.ofObjectStableId(originalStableId),
            ExecutionValue.of("done"))

        // Whole document deleted — the step's id becomes unresolvable
        mapper.apply(DeletedDocumentEvent(DocumentPath.parse("a.yaml")))

        val snapshot = store.lookup(runExecutionId, LogicTraceQuery(LogicTracePath.root))
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
