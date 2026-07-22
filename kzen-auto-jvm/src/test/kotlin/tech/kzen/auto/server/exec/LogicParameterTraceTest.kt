package tech.kzen.auto.server.exec

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Node
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull


/**
 * The flavour-neutral parameter-value trace contract ([LogicParameterTrace]): each declared parameter's
 * resolved run value (bound argument falling back to the declared default) is emitted once at run start, at
 * the parameter's own stable-id address, as a bounded PLAIN display value — not a Script StepTrace — with null
 * as [NullExecutionValue]. The signature editor reads these addresses to show the actual run value beside the
 * declared default, uniformly for Script and Job. Also pins the
 * [bind][tech.kzen.auto.server.objects.script.api.StepExecution.bind] contract the first iteration of this
 * feature broke: a loop-item binding records its value with NO trace entry (nothing reads its address).
 * Runs real notation on a real [RunEngine] and reads the engine's live trace directly
 * (the [ScriptTraceBoundingTest][tech.kzen.auto.server.exec.script.ScriptTraceBoundingTest] pattern).
 */
class LogicParameterTraceTest {
    //-----------------------------------------------------------------------------------------------------------------
    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //--------------------------------------------------------------------------------------------------------- Script
    @Test
    fun scriptParameterDefaultEmitsPlainValueAtItsAddress() {
        runLogic("test/script-engine-parameter-test.yaml") { engine, documentPath ->
            assertEquals(
                ExecutionValue.of("2"),
                liveValue(engine, documentPath, "main.parameters/Start"),
                "a bare run resolves the declared default, emitted as a plain (non-StepTrace) display value")
        }
    }


    @Test
    fun scriptParameterBoundArgumentEmitsAtItsAddress() {
        val inputs = TupleValue(listOf(
            TupleComponentValue(TupleComponentName("Start"), 5)))

        runLogic("test/script-engine-parameter-test.yaml", inputs) { engine, documentPath ->
            assertEquals(
                ExecutionValue.of("5"),
                liveValue(engine, documentPath, "main.parameters/Start"),
                "a bound argument overrides the declared default")
        }
    }


    @Test
    fun forEachItemBindingEmitsNoTrace() {
        runLogic("test/script-engine-foreach-test.yaml") { engine, documentPath ->
            assertNull(
                liveValue(engine, documentPath, "main.steps/Loop.item/Item"),
                "bind records a loop-item value with NO trace entry (see the StepExecution.bind contract)")
        }
    }


    //------------------------------------------------------------------------------------------------------------ Job
    @Test
    fun jobParameterBoundArgumentEmitsAtItsAddress() {
        val inputs = TupleValue(listOf(
            TupleComponentValue(TupleComponentName("items"), listOf(1, 2, 3))))

        runLogic("test/job-signature-child-test.yaml", inputs) { engine, documentPath ->
            assertEquals(
                ExecutionValue.of("[1, 2, 3]"),
                liveValue(engine, documentPath, "main.parameters/items"),
                "the Job's root node carries the bound argument's display value at the parameter's address")
        }
    }


    @Test
    fun jobUnboundDefaultlessParameterEmitsNull() {
        runLogic("test/job-signature-scalar-child-test.yaml") { engine, documentPath ->
            assertEquals(
                NullExecutionValue,
                liveValue(engine, documentPath, "main.parameters/item"),
                "null emits NullExecutionValue — hidden by the editor, while overwriting a stale prior value")
        }
    }


    //----------------------------------------------------------------------------------------------------- internals
    // The parameter's live trace value, read off the engine's node tree exactly as a trace query would;
    // null when nothing was emitted at its address.
    private fun liveValue(engine: RunEngine, documentPath: DocumentPath, objectPath: String): ExecutionValue? {
        val stableId = context.objectStableMapper.objectStableId(
            ObjectLocation(documentPath, ObjectPath.parse(objectPath)))
        return findLive(engine.snapshot().root, Address.of(stableId.value))
    }


    private fun findLive(node: Node, address: Address): ExecutionValue? {
        node.live[address]?.let { return it }
        for (child in node.children) {
            findLive(child, address)?.let { return it }
        }
        return null
    }


    private fun runLogic(
        documentPathString: String,
        inputs: TupleValue = TupleValue.empty,
        assertions: (RunEngine, DocumentPath) -> Unit
    ) {
        context = KzenAutoContext.forTest()

        val documentPath = DocumentPath.parse(documentPathString)
        val mainLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val logic = LogicCompiler.compile(
            mainLocation,
            graphNotation,
            graphDefinition,
            LogicCompilerServices(
                context.graphEnvironment,
                context.objectStableMapper,
                context.cachedKotlinCompiler,
                context.scriptValidationCache,
                context.jobValidationCache,
                context.notationMetadataReader,
                context.jobWorkPool,
                LogicRunExecutionId.random()))

        val engine = RunEngine(logic, context.objectStableMapper.objectStableId(mainLocation), inputs)
        try {
            val outcome = runBlocking {
                engine.resume()
                engine.await()
            }
            assertIs<Outcome.Success>(outcome, "run failed: $outcome")

            // Read the engine while it is still open — the trace lives in it (there is no separate store).
            assertions(engine, documentPath)
        }
        finally {
            engine.close()
        }
    }
}
