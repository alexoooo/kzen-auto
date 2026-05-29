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
import kotlin.test.assertTrue


/**
 * Rename-survival canary for LogicTraceStore.
 *
 * Move A made the mapper process-global so post-stop renames stay tracked. The trace wire then
 * moved to ObjectStableId keys (the client translates to the current location via its own mapper),
 * so on the server side `lookup` keeps stable-id keys verbatim through a rename and only drops an
 * entry once its object is deleted (id no longer resolves). The run↔root index used by
 * `mostRecent`/`clear` is likewise keyed by stable id, so it follows a rename of the run root.
 *
 * Post-Move B: LogicTraceStore is a constructor-injected instance rather than a singleton, so each
 * test owns its own store — no shared @After cleanup needed.
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
    fun `trace stays under its stable id after a rename event arrives post-stop`() {
        val mapper = ObjectStableMapper()
        val store = LogicTraceStore(mapper)

        // Simulate boot pre-warm
        mapper.objectStableId(rootLocation)
        val originalStableId = mapper.objectStableId(stepLocation)

        // Active run writes a trace entry under the step's stable id
        val handle = store.handle(runExecutionId, rootLocation)
        val stablePath = LogicTracePath.ofObjectStableId(originalStableId)
        handle.set(stablePath, ExecutionValue.of("done"))

        // Run terminates — buffer stays in the store; mapper keeps observing
        // (no unobserve in the Move-A model)

        // Rename event arrives at the global mapper AFTER the run ended
        mapper.apply(RenamedObjectEvent(stepLocation, ObjectName("Step1Renamed")))

        // The wire stays keyed by the (immutable) stable id — rename doesn't rewrite it; the client
        // resolves it to the current location via its own mapper.
        val snapshot = store.lookup(runExecutionId, LogicTraceQuery(LogicTracePath.root))
        checkNotNull(snapshot)
        assertEquals(ExecutionValue.of("done"), snapshot.values[stablePath])

        // Server no longer emits location-keyed paths (neither old nor new name)
        assertNull(snapshot.values[LogicTracePath.ofObjectLocation(stepLocation)])
        assertNull(snapshot.values[LogicTracePath.ofObjectLocation(objectLocation("a.yaml", "Step1Renamed"))])
    }


    @Test
    fun `trace stays retained through a chain of renames after run-stop`() {
        val mapper = ObjectStableMapper()
        val store = LogicTraceStore(mapper)
        mapper.objectStableId(rootLocation)
        val originalStableId = mapper.objectStableId(stepLocation)

        val handle = store.handle(runExecutionId, rootLocation)
        val stablePath = LogicTracePath.ofObjectStableId(originalStableId)
        handle.set(stablePath, ExecutionValue.of("done"))

        // A -> B -> C
        mapper.apply(RenamedObjectEvent(stepLocation, ObjectName("Mid")))
        mapper.apply(RenamedObjectEvent(objectLocation("a.yaml", "Mid"), ObjectName("Final")))

        // Still resolvable (object exists under "Final"), so still retained under its stable id
        val snapshot = store.lookup(runExecutionId, LogicTraceQuery(LogicTracePath.root))
        checkNotNull(snapshot)
        assertEquals(ExecutionValue.of("done"), snapshot.values[stablePath])
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


    @Test
    fun `most recent and clear survive a rename of the run root`() {
        val mapper = ObjectStableMapper()
        val store = LogicTraceStore(mapper)
        mapper.objectStableId(rootLocation)

        // Run scoped to the root document's main object
        store.handle(runExecutionId, rootLocation)

        // Root object renamed after the run ended
        mapper.apply(RenamedObjectEvent(rootLocation, ObjectName("MyScriptRenamed")))
        val renamedRoot = objectLocation("a.yaml", "MyScriptRenamed")

        // The run↔root index is keyed by stable id, so it follows the rename
        assertEquals(runExecutionId, store.mostRecent(renamedRoot))
        assertTrue(store.clear(renamedRoot))
        assertNull(store.mostRecent(renamedRoot))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun objectLocation(documentPath: String, objectName: String): ObjectLocation {
        return ObjectLocation(
            DocumentPath.parse(documentPath),
            ObjectPath.parse(objectName))
    }
}
