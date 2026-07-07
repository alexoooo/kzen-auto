package tech.kzen.auto.server.objects.logic

import org.junit.Test
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
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
    private val runExecutionId = LogicRunExecutionId.random()
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
        val handle = store.handle(runExecutionId, rootLocation, null, null)
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
        assertEquals(ExecutionValue.of("done"), snapshot.values[stablePath]?.value)

        // Location-keyed paths are never emitted — neither the original nor the renamed name appears
        assertNull(snapshot.values[LogicTracePath.ofObjectLocation(stepLocation)])
        assertNull(snapshot.values[LogicTracePath.ofObjectLocation(objectLocation("a.yaml", "Step1Renamed"))])
    }


    @Test
    fun `trace stays retained through a chain of renames after run-stop`() {
        val mapper = ObjectStableMapper()
        val store = LogicTraceStore(mapper)
        mapper.objectStableId(rootLocation)
        val originalStableId = mapper.objectStableId(stepLocation)

        val handle = store.handle(runExecutionId, rootLocation, null, null)
        val stablePath = LogicTracePath.ofObjectStableId(originalStableId)
        handle.set(stablePath, ExecutionValue.of("done"))

        // A -> B -> C
        mapper.apply(RenamedObjectEvent(stepLocation, ObjectName("Mid")))
        mapper.apply(RenamedObjectEvent(objectLocation("a.yaml", "Mid"), ObjectName("Final")))

        // Still resolvable (object exists under "Final"), so still retained under its stable id
        val snapshot = store.lookup(runExecutionId, LogicTraceQuery(LogicTracePath.root))
        checkNotNull(snapshot)
        assertEquals(ExecutionValue.of("done"), snapshot.values[stablePath]?.value)
    }


    @Test
    fun `trace entry drops when its object is deleted from the graph`() {
        val mapper = ObjectStableMapper()
        val store = LogicTraceStore(mapper)
        mapper.objectStableId(rootLocation)
        val originalStableId = mapper.objectStableId(stepLocation)

        val handle = store.handle(runExecutionId, rootLocation, null, null)
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
        store.handle(runExecutionId, rootLocation, null, null)

        // Root object renamed after the run ended
        mapper.apply(RenamedObjectEvent(rootLocation, ObjectName("MyScriptRenamed")))
        val renamedRoot = objectLocation("a.yaml", "MyScriptRenamed")

        // The run↔root index is keyed by stable id, so it follows the rename
        assertEquals(runExecutionId, store.mostRecent(renamedRoot))
        assertTrue(store.clear(renamedRoot))
        assertNull(store.mostRecent(renamedRoot))
    }


    @Test
    fun `re-invoked sub-logic clears the previous iteration's live values but keeps history`() {
        val mapper = ObjectStableMapper()
        val store = LogicTraceStore(mapper)

        val runId = LogicRunExecutionId.random().logicRunId
        val itemRoot = objectLocation("item.yaml", "Item")
        val itemStep = objectLocation("item.yaml", "ItemStep")
        mapper.objectStableId(itemRoot)
        val stepStableId = mapper.objectStableId(itemStep)
        val stepPath = LogicTracePath.ofObjectStableId(stepStableId)

        // Iteration 1: the sub-logic runs under its own execution id and traces a step value + event.
        val iter1 = LogicRunExecutionId(runId, LogicExecutionId.random())
        val handle1 = store.handle(iter1, itemRoot, null, null)
        handle1.set(stepPath, ExecutionValue.of("iter1"))
        handle1.append(stepStableId, ExecutionValue.of("iter1"))

        val afterIter1 = store.lookupRun(runId, LogicTraceQuery(LogicTracePath.root))
        checkNotNull(afterIter1)
        assertEquals(ExecutionValue.of("iter1"), afterIter1.values[stepPath]?.value)

        // Iteration 2: the same sub-logic is re-invoked (fresh execution id, same run). Before it traces
        // anything, the whole-run merge must NOT still show iteration 1's finished value.
        val iter2 = LogicRunExecutionId(runId, LogicExecutionId.random())
        val handle2 = store.handle(iter2, itemRoot, null, null)

        val betweenIterations = store.lookupRun(runId, LogicTraceQuery(LogicTracePath.root))
        checkNotNull(betweenIterations)
        assertNull(betweenIterations.values[stepPath])

        // But the append-only event history is retained across the iteration boundary (film strip).
        val historyEvents = store.lookupRunHistory(runId, 0L)
        assertEquals(1, historyEvents.size)
        assertEquals(ExecutionValue.of("iter1"), historyEvents.single().value)

        // Iteration 2 traces its own value, which then drives the live view.
        handle2.set(stepPath, ExecutionValue.of("iter2"))
        val afterIter2 = store.lookupRun(runId, LogicTraceQuery(LogicTracePath.root))
        checkNotNull(afterIter2)
        assertEquals(ExecutionValue.of("iter2"), afterIter2.values[stepPath]?.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun objectLocation(documentPath: String, objectName: String): ObjectLocation {
        return ObjectLocation(
            DocumentPath.parse(documentPath),
            ObjectPath.parse(objectName))
    }
}
