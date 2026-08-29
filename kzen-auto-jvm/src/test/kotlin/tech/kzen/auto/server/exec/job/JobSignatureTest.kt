package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.mainBoundaryValue
import tech.kzen.auto.server.objects.job.worker.test.RecordingSinkWorker
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * End-to-end for the Job signature (J2): a Job declares typed parameters (`parameters` branch ParameterBinding
 * declarations, sourced by a FormulaSource expression) and a typed `results` signature its ResultSink Workers
 * yield into (keeping a single first/last element of the stream), so it can be hosted with arguments and its
 * result consumed — completing the "flavours nest each other uniformly" story. Covers the direct engine round
 * trip (argument in -> stream -> keep -> harvest -> tuple out, scalar and stream lanes, plus a bare unbound run
 * and the empty-stream contract: null under a nullable declared result, a run failure under a non-nullable one),
 * a Script RunStep hosting a parameterized Job, a Job RunWorker hosting one per element, and the
 * appendix-mandated frame-trace pin (distinct retained executions per hosted invocation, sharing the child's
 * stable id). Jobs are nondeterministic, so assertions are on the harvested result / recorded set, never
 * interleaving order.
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
        val jobLogic = compile("test/job/signature/job-signature-child-test.yaml")
        val signature = jobLogic.signature()

        assertEquals(
            listOf("items"), signature.inputs.definitions.map { it.name.value },
            "the `items` parameter declaration is the Job's input signature")
        assertEquals(
            listOf("main"), signature.outputs.definitions.map { it.name.value },
            "the document's declared `results` map declares the `main` output component")

        val outcome = runToCompletion(
            jobLogic, "test/job/signature/job-signature-child-test.yaml",
            inputs(jobLogic, "items" to listOf(1, 2, 3)))

        val success = assertIs<Outcome.Success>(outcome)
        assertEquals(
            3, success.value.mainBoundaryValue(),
            "the argument reached the source stream AND the sink kept the LAST element (default `keep`)")
    }


    @Test
    fun scalarArgumentYieldsScalarResult() {
        // The SCALAR child: `item` is declared nullable Any (not an Iterable type), so strict-static dispatch
        // single-emits the bound value regardless of what it is.
        val jobLogic = compile("test/job/signature/job-signature-scalar-child-test.yaml")
        val outcome = runToCompletion(
            jobLogic, "test/job/signature/job-signature-scalar-child-test.yaml", inputs(jobLogic, "item" to 7))

        val success = assertIs<Outcome.Success>(outcome)
        assertEquals(
            7, success.value.mainBoundaryValue(),
            "a scalar-typed parameter emits one element, which the sink keeps (scalar-in / scalar-out)")
    }


    @Test
    fun unboundScalarParameterEmitsSingleNull() {
        val jobLogic = compile("test/job/signature/job-signature-scalar-child-test.yaml")
        val outcome = runToCompletion(
            jobLogic, "test/job/signature/job-signature-scalar-child-test.yaml", emptyInputs(jobLogic))

        val success = assertIs<Outcome.Success>(outcome)
        assertTrue(
            success.value.schema.find(BindingName("main")) != null,
            "the sink still yields a `main` component for a bare run")
        assertEquals(
            null, success.value.mainBoundaryValue(),
            "an unbound scalar-typed parameter emits a single null, collected and unwrapped to null")
    }


    @Test
    fun bareRunOfIterableTypedSourceEmitsNothing() {
        // Strict-static dispatch: the List-typed `items` declaration makes the source a STREAM lane, so a bare
        // run's null value is an empty stream, not a null element — and the child's declared result is NULLABLE,
        // so the empty stream yields null instead of failing the run.
        val jobLogic = compile("test/job/signature/job-signature-child-test.yaml")
        val outcome = runToCompletion(
            jobLogic, "test/job/signature/job-signature-child-test.yaml", emptyInputs(jobLogic))

        val success = assertIs<Outcome.Success>(outcome)
        assertTrue(
            success.value.schema.find(BindingName("main")) != null,
            "the sink still yields the `main` component for an empty stream")
        assertEquals(
            null, success.value.mainBoundaryValue(),
            "an empty stream under a NULLABLE declared result is a null result, not a failure")
    }


    @Test
    fun keepFirstYieldsFirstElement() {
        val jobLogic = compile("test/job/signature/job-result-first-test.yaml")
        val outcome = runToCompletion(
            jobLogic, "test/job/signature/job-result-first-test.yaml", emptyInputs(jobLogic))

        val success = assertIs<Outcome.Success>(outcome)
        assertEquals(
            1, success.value.mainBoundaryValue(),
            "`keep: first` yields the stream's first element (the stream still drains to completion)")
    }


    @Test
    fun emptyStreamWithNonNullableResultFailsTheRun() {
        // The empty-stream contract's strict half: the declared `main` result is NON-nullable, so an empty
        // stream at end-of-stream is a run failure (the sink's error names the component), never a silent null.
        val jobLogic = compile("test/job/signature/job-result-empty-test.yaml")
        val outcome = runToCompletion(
            jobLogic, "test/job/signature/job-result-empty-test.yaml", emptyInputs(jobLogic))

        assertIs<Outcome.Failed>(
            outcome,
            "an empty stream under a NON-nullable declared result fails the run")
    }


    @Test
    fun iterableTypedParameterBoundToScalarFailsTheRun() {
        // The typed parameter accessor enforces the declaration: a non-List value bound to the List-typed
        // `items` fails the cast when the expression reads it — a run failure naming the violation, never a
        // silent wrong-shaped stream (Script's typed-binding contract).
        val jobLogic = compile("test/job/signature/job-signature-child-test.yaml")
        assertFailsWith<DataException>(
            "a scalar bound to an Iterable-typed parameter is rejected before child code") {
            runToCompletion(
                jobLogic, "test/job/signature/job-signature-child-test.yaml", inputs(jobLogic, "items" to 7))
        }
    }


    @Test
    fun scriptRunStepBindsArgumentsIntoHostedJob() {
        val notation = AutoTestUtils.readNotation()
        context = KzenAutoContext.forTest()

        val scriptLocation = ObjectLocation(
            DocumentPath.parse("test/job/signature/job-signature-script-test.yaml"), ObjectPath.parse("main"))
        val scriptLogic = LogicCompiler.compile(
            scriptLocation, notation, definition(notation), services())

        val outcome = runEngine(scriptLogic, context.objectStableMapper.objectStableId(scriptLocation))

        val success = assertIs<Outcome.Success>(outcome)
        assertEquals(
            3, success.value.mainBoundaryValue(),
            "the RunStep's named-argument tuple seeds the hosted Job, whose harvested (kept-last) result is " +
                "the Script result")
    }


    @Test
    fun runWorkerHostsParameterizedJobPerElement() {
        RecordingSinkWorker.reset()
        val jobLogic = compile("test/job/signature/job-signature-nested-test.yaml")
        val outcome = runEngine(
            jobLogic,
            context.objectStableMapper.objectStableId(mainOf("test/job/signature/job-signature-nested-test.yaml")))

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
        val jobLogic = compile("test/job/run/job-run-host-test.yaml")

        val engine = RunEngine(
            jobLogic, context.objectStableMapper.objectStableId(mainOf("test/job/run/job-run-host-test.yaml")))
        try {
            val outcome = runBlocking {
                engine.resume()
                engine.await()
            }
            assertIs<Outcome.Success>(outcome)

            val runStableId = context.objectStableMapper.objectStableId(
                ObjectLocation(
                    DocumentPath.parse("test/job/run/job-run-host-test.yaml"), ObjectPath.parse("main.workers/run")))
            val runNode = engine.snapshot().root.children.first { it.stableId == runStableId }

            assertEquals(
                3, runNode.children.size, "the RunWorker hosted the child three times (one per element)")
            assertEquals(
                3, runNode.children.map { it.id }.toSet().size,
                "each hosted invocation is its own execution node (distinct NodeId, fresh trace scope)")

            val childStableId = context.objectStableMapper.objectStableId(
                ObjectLocation(
                    DocumentPath.parse("test/script/engine/script-engine-child-test.yaml"), ObjectPath.parse("main")))
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


    private fun runToCompletion(jobLogic: JobLogic, path: String, inputs: DataBindings): Outcome {
        return runEngine(jobLogic, context.objectStableMapper.objectStableId(mainOf(path)), inputs)
    }


    private fun runEngine(
        logic: Logic,
        rootStableId: ObjectStableId,
        inputs: DataBindings = emptyInputs(logic)
    ): Outcome {
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


    private fun inputs(logic: Logic, vararg components: Pair<String, Any?>): DataBindings {
        val schema = logic.signature().inputs
        return DataBindings.bind(schema, components.map { (name, value) ->
            val bindingName = BindingName(name)
            val definition = requireNotNull(schema.find(bindingName))
            bindingName to JobDataValues.lift(value, definition.contract)
        })
    }


    private fun emptyInputs(logic: Logic): DataBindings =
        DataBindings.bind(logic.signature().inputs)


    private fun definition(notation: GraphNotation) =
        AutoTestUtils.graphDefinitionAttempt(notation).transitiveSuccessful


    private fun services() = LogicCompilerServices(
        context.graphEnvironment,
        context.objectStableMapper,
        context.cachedKotlinCompiler,
        context.scriptValidationCache,
        context.jobValidationCache,
        context.notationMetadataReader,
        context.jobWorkPool,
        LogicRunExecutionId.random())
}
