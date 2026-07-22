package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.objects.job.worker.test.RecordingSinkWorker
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * End-to-end for the Job signature (J2): a Job declares typed parameters (`parameters` branch ParameterBinding
 * declarations, sourced by a FormulaSource expression) and produces an output tuple (ResultSink Workers), so it can
 * be hosted with arguments and its result consumed — completing the "flavours nest each other uniformly" story. Covers the direct engine round trip (argument in -> stream -> collect -> harvest
 * -> tuple out, scalar and collection, plus a bare unbound run), a Script RunStep hosting a parameterized Job, a Job
 * RunWorker hosting one per element, and the appendix-mandated frame-trace pin (distinct retained executions per
 * hosted invocation, sharing the child's stable id). Jobs are nondeterministic, so assertions are on the harvested
 * result / recorded set, never interleaving order.
 */
class JobSignatureTest {
    //-----------------------------------------------------------------------------------------------------------------
    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun directRunSeedsParameterAndHarvestsResult() {
        val jobLogic = compile("test/job-signature-child-test.yaml")

        assertEquals(
            listOf("items"), jobLogic.signature().inputs.components.map { it.name.value },
            "the `items` parameter declaration is the Job's input signature")
        assertEquals(
            listOf("main"), jobLogic.signature().outputs.components.map { it.name.value },
            "the ResultSink worker (blank result) declares the `main` output component")

        val outcome = runToCompletion(
            jobLogic, "test/job-signature-child-test.yaml",
            inputs("items" to listOf(1, 2, 3)))

        val success = assertIs<Outcome.Success>(outcome)
        assertEquals(
            listOf(1, 2, 3), success.value.mainComponentValue(),
            "the argument reached the source stream AND the collected result round-tripped back, order preserved")
    }


    @Test
    fun scalarArgumentYieldsScalarResult() {
        val jobLogic = compile("test/job-signature-child-test.yaml")
        val outcome = runToCompletion(
            jobLogic, "test/job-signature-child-test.yaml", inputs("items" to 7))

        val success = assertIs<Outcome.Success>(outcome)
        assertEquals(
            7, success.value.mainComponentValue(),
            "a non-Collection argument streams as one element and unwraps back to a scalar (scalar-in / scalar-out)")
    }


    @Test
    fun unboundParameterStreamsSingleNull() {
        val jobLogic = compile("test/job-signature-child-test.yaml")
        val outcome = runToCompletion(
            jobLogic, "test/job-signature-child-test.yaml", TupleValue.empty)

        val success = assertIs<Outcome.Success>(outcome)
        assertTrue(
            success.value.components.any { it.name == TupleComponentName.main },
            "the sink still yields a `main` component for a bare run")
        assertEquals(
            null, success.value.mainComponentValue(),
            "an unbound parameter streams a single null, collected and unwrapped to null")
    }


    @Test
    fun scriptRunStepBindsArgumentsIntoHostedJob() {
        val notation = AutoTestUtils.readNotation()
        context = KzenAutoContext.forTest()

        val scriptLocation = ObjectLocation(
            DocumentPath.parse("test/job-signature-script-test.yaml"), ObjectPath.parse("main"))
        val scriptLogic = LogicCompiler.compile(
            scriptLocation, notation, definition(notation), services())

        val outcome = runEngine(scriptLogic, context.objectStableMapper.objectStableId(scriptLocation))

        val success = assertIs<Outcome.Success>(outcome)
        assertEquals(
            listOf(1, 2, 3), success.value.mainComponentValue(),
            "the RunStep's named-argument tuple seeds the hosted Job, whose harvested result is the Script result")
    }


    @Test
    fun runWorkerHostsParameterizedJobPerElement() {
        RecordingSinkWorker.reset()
        val jobLogic = compile("test/job-signature-nested-test.yaml")
        val outcome = runEngine(
            jobLogic,
            context.objectStableMapper.objectStableId(mainOf("test/job-signature-nested-test.yaml")))

        assertIs<Outcome.Success>(outcome)
        assertEquals(
            setOf(1, 2, 3), RecordingSinkWorker.recorded().toSet(),
            "each element hosts the parameterized child Job once: streamed as one element, collected, unwrapped, " +
                "yielded as main, emitted downstream")
    }


    @Test
    fun hostedChildInvocationsAreDistinctRetainedExecutions() {
        // Frame-trace pin (appendix check): a RunWorker hosting a child per element produces one execution node per
        // invocation — distinct NodeIds, all sharing the child document's stable id (fresh trace scope per
        // re-entry) — and settled invocations stay retained under the current retainTrace = true default. Replaces
        // the retired JobNestedLogicTest pins at the engine grain (the Script-side ghosting pin lives in
        // SubScriptTraceScopingTest).
        RecordingSinkWorker.reset()
        val jobLogic = compile("test/job-run-host-test.yaml")

        val engine = RunEngine(
            jobLogic, context.objectStableMapper.objectStableId(mainOf("test/job-run-host-test.yaml")))
        try {
            val outcome = runBlocking {
                engine.resume()
                engine.await()
            }
            assertIs<Outcome.Success>(outcome)

            val runStableId = context.objectStableMapper.objectStableId(
                ObjectLocation(
                    DocumentPath.parse("test/job-run-host-test.yaml"), ObjectPath.parse("main.workers/run")))
            val runNode = engine.snapshot().root.children.first { it.stableId == runStableId }

            assertEquals(
                3, runNode.children.size, "the RunWorker hosted the child three times (one per element)")
            assertEquals(
                3, runNode.children.map { it.id }.toSet().size,
                "each hosted invocation is its own execution node (distinct NodeId, fresh trace scope)")

            val childStableId = context.objectStableMapper.objectStableId(
                ObjectLocation(
                    DocumentPath.parse("test/script-engine-child-test.yaml"), ObjectPath.parse("main")))
            assertTrue(
                runNode.children.all { it.stableId == childStableId },
                "every hosted invocation carries the child document's stable id")
        }
        finally {
            engine.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun compile(path: String): JobLogic {
        context = KzenAutoContext.forTest()
        val notation = AutoTestUtils.readNotation()
        return JobLogicCompiler.compile(mainOf(path), notation, definition(notation), services())
    }


    private fun runToCompletion(jobLogic: JobLogic, path: String, inputs: TupleValue): Outcome {
        return runEngine(jobLogic, context.objectStableMapper.objectStableId(mainOf(path)), inputs)
    }


    private fun runEngine(logic: Logic, rootStableId: ObjectStableId, inputs: TupleValue = TupleValue.empty): Outcome {
        val engine = RunEngine(logic, rootStableId, inputs)
        return try {
            runBlocking {
                engine.resume()
                engine.await()
            }
        }
        finally {
            engine.close()
        }
    }


    private fun mainOf(path: String): ObjectLocation {
        return ObjectLocation(DocumentPath.parse(path), ObjectPath.parse("main"))
    }


    private fun inputs(vararg components: Pair<String, Any?>): TupleValue {
        return TupleValue(components.map { (name, value) -> TupleComponentValue(TupleComponentName(name), value) })
    }


    private fun definition(notation: GraphNotation) =
        AutoTestUtils.graphDefinitionAttempt(notation).transitiveSuccessful


    private fun services() = LogicCompilerServices(
        context.graphEnvironment,
        context.objectStableMapper,
        context.cachedKotlinCompiler,
        context.scriptValidationCache,
        context.notationMetadataReader,
        context.jobWorkPool,
        LogicRunExecutionId.random())
}
