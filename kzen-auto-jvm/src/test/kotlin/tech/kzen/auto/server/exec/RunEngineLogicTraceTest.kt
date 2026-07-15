package tech.kzen.auto.server.exec

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.cqrs.DeletedDocumentEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedObjectEvent
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * Unit tests for [RunEngineLogicTrace] — the query-time projection of a [RunEngine] that replaced the retired
 * LogicTraceStore. Covers what the former LogicTraceStoreRenameTest / LogicTraceStoreExecutionTreeTest asserted
 * on the store directly, now against the engine: rename survival (paths stay stable-id-keyed, resolved via the
 * process-global [ObjectStableMapper]), delete-drop, mostRecent / clear, the execution tree, and the whole-run
 * merge's latest-node-per-stable-id rule (the generic reproduction of the store's re-entry clearing).
 *
 * Each test builds a real [RunEngine] over a tiny [Logic], runs it to completion, and wraps it in a
 * [RunEngineLogicTrace] with a fake [RunTraceAccess] (no controller needed).
 */
class RunEngineLogicTraceTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val runId = LogicRunId("test-run")
    private val rootLocation = objectLocation("a.yaml", "MyScript")
    private val stepLocation = objectLocation("a.yaml", "Step1")

    // The engine assigns node ids in creation order — "n0" is always the root.
    private val rootExecution = LogicRunExecutionId(runId, LogicExecutionId("n0"))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `trace stays under its stable id after a rename`() = runBlocking {
        val mapper = ObjectStableMapper()
        mapper.objectStableId(rootLocation)
        val stepStableId = mapper.objectStableId(stepLocation)

        val engine = runToCompletion(mapper, RunEngine(logic { execution ->
            execution.emit(Address.of(stepStableId.value), ExecutionValue.of("done"))
            TupleValue.ofMain("ok")
        }, mapper.objectStableId(rootLocation)))
        try {
            val trace = traceFor(mapper, engine)

            // Rename arrives at the (process-global) mapper after the run ended.
            mapper.apply(RenamedObjectEvent(stepLocation, ObjectName("Step1Renamed")))

            val stablePath = LogicTracePath.ofObjectStableId(stepStableId)
            val snapshot = trace.lookup(rootExecution, LogicTraceQuery(LogicTracePath.root))
            assertNotNull(snapshot)
            assertEquals(ExecutionValue.of("done"), snapshot.values[stablePath]?.value,
                "wire stays keyed by the immutable stable id — rename doesn't rewrite it")

            // Location-keyed paths are never emitted — neither the original nor the renamed name appears.
            assertNull(snapshot.values[LogicTracePath.ofObjectLocation(stepLocation)])
            assertNull(snapshot.values[LogicTracePath.ofObjectLocation(objectLocation("a.yaml", "Step1Renamed"))])
        }
        finally {
            engine.dispose()
        }
    }


    @Test
    fun `trace entry drops when its object is deleted`() = runBlocking {
        val mapper = ObjectStableMapper()
        mapper.objectStableId(rootLocation)
        val stepStableId = mapper.objectStableId(stepLocation)

        val engine = runToCompletion(mapper, RunEngine(logic { execution ->
            execution.emit(Address.of(stepStableId.value), ExecutionValue.of("done"))
            TupleValue.ofMain("ok")
        }, mapper.objectStableId(rootLocation)))
        try {
            val trace = traceFor(mapper, engine)

            // Whole document deleted — the step's id becomes unresolvable, so it drops from the snapshot.
            mapper.apply(DeletedDocumentEvent(DocumentPath.parse("a.yaml")))

            val snapshot = trace.lookup(rootExecution, LogicTraceQuery(LogicTracePath.root))
            assertNotNull(snapshot)
            assertEquals(emptyMap(), snapshot.values)
        }
        finally {
            engine.dispose()
        }
    }


    @Test
    fun `mostRecent and clear survive a rename of the run root`() = runBlocking {
        val mapper = ObjectStableMapper()
        val rootStableId = mapper.objectStableId(rootLocation)

        val engine = runToCompletion(mapper, RunEngine(logic { TupleValue.ofMain("ok") }, rootStableId))
        try {
            val handle = FakeRun(runId, engine)
            val trace = traceFor(mapper, handle)

            mapper.apply(RenamedObjectEvent(rootLocation, ObjectName("MyScriptRenamed")))
            val renamedRoot = objectLocation("a.yaml", "MyScriptRenamed")

            // The run's root node is discoverable by its (renamed) location — mostRecent follows the rename.
            assertEquals(rootExecution, trace.mostRecent(renamedRoot))

            // clear disposes the retained run; mostRecent then finds nothing.
            assertTrue(trace.clear(renamedRoot))
            assertNull(trace.mostRecent(renamedRoot))
        }
        finally {
            engine.dispose()
        }
    }


    @Test
    fun `sibling invocations of the same sub-logic get distinct execution rows sharing a parent`() = runBlocking {
        val mapper = ObjectStableMapper()
        val rootStableId = mapper.objectStableId(rootLocation)
        val subStableId = mapper.objectStableId(objectLocation("sub.yaml", "Sub"))
        val callA = mapper.objectStableId(objectLocation("a.yaml", "RunA"))
        val callB = mapper.objectStableId(objectLocation("a.yaml", "RunB"))

        val engine = runToCompletion(mapper, RunEngine(logic { execution ->
            execution.host(subStableId, logic { TupleValue.ofMain("a") }, callerStableId = callA)
            execution.host(subStableId, logic { TupleValue.ofMain("b") }, callerStableId = callB)
            TupleValue.ofMain("ok")
        }, rootStableId))
        try {
            val trace = traceFor(mapper, engine)

            val executions = trace.lookupRunExecutions(runId)
            assertEquals(3, executions.size)

            val rootRow = executions.single { it.executionId == LogicExecutionId("n0") }
            assertNull(rootRow.parentExecutionId)
            assertNull(rootRow.callerStableId)

            val children = executions.filter { it.parentExecutionId != null }
            assertEquals(2, children.size, "each sub-logic invocation is its own execution")
            assertEquals(setOf(LogicExecutionId("n0")), children.map { it.parentExecutionId }.toSet(),
                "both share the root as parent")
            assertEquals(setOf(callA, callB), children.map { it.callerStableId }.toSet(),
                "distinct call-sites tell the two invocations of the same document apart")
        }
        finally {
            engine.dispose()
        }
    }


    @Test
    fun `whole-run merge keeps only the latest invocation per stable id, but history retains both`() = runBlocking {
        val mapper = ObjectStableMapper()
        val rootStableId = mapper.objectStableId(rootLocation)
        val subStableId = mapper.objectStableId(objectLocation("sub.yaml", "Sub"))
        val subStep = mapper.objectStableId(objectLocation("sub.yaml", "SubStep"))
        val onlyInFirst = mapper.objectStableId(objectLocation("sub.yaml", "OnlyFirst"))

        val engine = runToCompletion(mapper, RunEngine(logic { execution ->
            // First invocation of the sub-logic (node n1): emits a shared step value, an extra value, and a
            // history event.
            execution.host(subStableId, logic { child ->
                child.emit(Address.of(subStep.value), ExecutionValue.of("iter1"))
                child.emit(Address.of(onlyInFirst.value), ExecutionValue.of("extra"))
                child.log(ExecutionValue.of("hist1"))
                TupleValue.ofMain("c1")
            })
            // Second invocation (node n2): re-emits only the shared step value.
            execution.host(subStableId, logic { child ->
                child.emit(Address.of(subStep.value), ExecutionValue.of("iter2"))
                TupleValue.ofMain("c2")
            })
            TupleValue.ofMain("ok")
        }, rootStableId))
        try {
            val trace = traceFor(mapper, engine)

            // Whole-run merge: only the LATEST invocation of the sub-logic contributes, so the shared step
            // shows iter2 and the first invocation's extra value is NOT present (superseded, dropped).
            val merged = trace.lookupRun(runId, LogicTraceQuery(LogicTracePath.root))
            assertNotNull(merged)
            assertEquals(ExecutionValue.of("iter2"),
                merged.values[LogicTracePath.ofObjectStableId(subStep)]?.value)
            assertNull(merged.values[LogicTracePath.ofObjectStableId(onlyInFirst)],
                "a superseded invocation's values drop out of the merged live view")

            // But single-execution lookup still resolves the FIRST invocation in isolation (per-invocation
            // scope for the RunStep view) — its extra value is intact there.
            val firstExecution = LogicRunExecutionId(runId, LogicExecutionId("n1"))
            val firstSnapshot = trace.lookup(firstExecution, LogicTraceQuery(LogicTracePath.root))
            assertNotNull(firstSnapshot)
            assertEquals(ExecutionValue.of("extra"),
                firstSnapshot.values[LogicTracePath.ofObjectStableId(onlyInFirst)]?.value)

            // The append-only film-strip (log events) survives across the superseded invocation.
            val history = trace.lookupRunHistory(runId, 0L)
            assertEquals(1, history.size)
            assertEquals(ExecutionValue.of("hist1"), history.single().value)
        }
        finally {
            engine.dispose()
        }
    }


    @Test
    fun `a stale run id resolves to nothing`() = runBlocking {
        val mapper = ObjectStableMapper()
        val engine = runToCompletion(mapper, RunEngine(
            logic { TupleValue.ofMain("ok") }, mapper.objectStableId(rootLocation)))
        try {
            val trace = traceFor(mapper, engine)
            assertNull(trace.lookupRun(LogicRunId("other-run"), LogicTraceQuery(LogicTracePath.root)))
            assertFalse(trace.lookupRunHistory(LogicRunId("other-run"), 0L).isNotEmpty())
        }
        finally {
            engine.dispose()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private class FakeRun(val runId: LogicRunId, val engine: RunEngine) {
        var cleared = false
    }


    private fun traceFor(mapper: ObjectStableMapper, engine: RunEngine): RunEngineLogicTrace =
        traceFor(mapper, FakeRun(runId, engine))


    private fun traceFor(mapper: ObjectStableMapper, run: FakeRun): RunEngineLogicTrace =
        RunEngineLogicTrace(
            mapper,
            emptyList(),
            { if (run.cleared) null else RunTraceAccess(run.runId, run.engine) },
            { run.cleared = true; true })


    private suspend fun runToCompletion(@Suppress("UNUSED_PARAMETER") mapper: ObjectStableMapper, engine: RunEngine): RunEngine {
        engine.resume()
        engine.await()
        engine.shutdown()
        return engine
    }


    private fun logic(block: suspend (Execution) -> TupleValue): Logic =
        object : Logic {
            override fun signature() = LogicSignature.empty
            override suspend fun run(execution: Execution): TupleValue = block(execution)
        }


    private fun objectLocation(documentPath: String, objectName: String): ObjectLocation =
        ObjectLocation(DocumentPath.parse(documentPath), ObjectPath.parse(objectName))
}
