package tech.kzen.auto.server.objects.flow

import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.logic.Logic
import tech.kzen.lib.common.exec.logic.LogicExecution
import tech.kzen.lib.common.exec.logic.LogicExecutionFacade
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.model.LogicResultFailed
import tech.kzen.lib.common.exec.logic.model.LogicResultPaused
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.logic.context.MutableLogicControl
import tech.kzen.lib.server.exec.logic.context.MutableLogicResourceScope
import kotlin.test.assertEquals
import kotlin.test.assertIs


/**
 * Drives [FlowDocument] / [FlowExecution] over the real notation -> graph -> Logic path in-process
 * (no server, no SUT subprocess), mirroring [tech.kzen.auto.server.objects.script.ScriptExecutionPauseTest].
 * Covers: input parameters flowing in as run arguments, the return value harvested from output vertices,
 * the logic signature derived from input/output vertices, and one-vertex-per-step execution.
 */
class FlowExecutionTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val executionDocumentPath = DocumentPath.parse("test/flow-execution-test.yaml")
    private val stepDocumentPath = DocumentPath.parse("test/flow-step-test.yaml")
    private val streamDocumentPath = DocumentPath.parse("test/flow-stream-test.yaml")
    private val errorDocumentPath = DocumentPath.parse("test/flow-error-test.yaml")

    private lateinit var context: KzenAutoContext


    //-----------------------------------------------------------------------------------------------------------------
    @Before
    fun setUp() {
        context = KzenAutoContext.forTest()
    }


    @After
    fun tearDown() {
        context.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun runProducesReturnValueFromArguments() {
        val execution = newExecution(executionDocumentPath)
        execution.beforeStart(argument("x", 42))

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition(executionDocumentPath))

        assertIs<LogicResultSuccess>(result)
        assertEquals(42, result.value.find(TupleComponentName("out")))
    }


    @Test
    fun defineReportsInputAndOutputVertices() {
        val definition = logic(executionDocumentPath).define()

        assertEquals(listOf("x"), definition.inputs.components.map { it.name.value })
        assertEquals(listOf("out"), definition.outputs.components.map { it.name.value })
    }


    @Test
    fun stepRunsOneVertexAtATime() {
        // Three vertices (Input -> Middle -> Output). With a Pause command pre-armed, each
        // continueOrStart pass runs exactly one vertex and re-pauses; the final pass completes the run.
        val execution = newExecution(stepDocumentPath)
        execution.beforeStart(argument("x", "ignored-by-replace"))

        val control = MutableLogicControl(false)
        control.commandPause()
        val resourceScope = MutableLogicResourceScope()
        val graphDefinition = graphDefinition(stepDocumentPath)

        assertEquals(LogicResultPaused,
            execution.continueOrStart(control, resourceScope, graphDefinition))
        assertEquals(LogicResultPaused,
            execution.continueOrStart(control, resourceScope, graphDefinition))

        val result = execution.continueOrStart(control, resourceScope, graphDefinition)
        assertIs<LogicResultSuccess>(result)
        assertEquals("Z", result.value.find(TupleComponentName("out")))
    }


    @Test
    fun runWithoutPauseCompletesInOnePass() {
        val execution = newExecution(stepDocumentPath)
        execution.beforeStart(argument("x", "ignored-by-replace"))

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition(stepDocumentPath))

        assertIs<LogicResultSuccess>(result)
        assertEquals("Z", result.value.find(TupleComponentName("out")))
    }


    @Test
    fun streamSourceDrivesMultipleIterationsToLastValue() {
        // IntRangeSource(1..3) emits across iterations; clearIterationForLoop resets the downstream
        // each pass so the output vertex re-runs, leaving the last streamed value as the result.
        val execution = newExecution(streamDocumentPath)
        execution.beforeStart(TupleValue.empty)

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition(streamDocumentPath))

        assertIs<LogicResultSuccess>(result)
        assertEquals(3, result.value.find(TupleComponentName("last")))
    }


    @Test
    fun vertexErrorFailsWithoutPauseOnError() {
        val execution = newExecution(errorDocumentPath)
        execution.beforeStart(argument("n", 6))

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition(errorDocumentPath))

        assertIs<LogicResultFailed>(result)
    }


    @Test
    fun vertexErrorPausesWithPauseOnErrorAndStaysNextToRun() {
        // With pause-on-error the failing vertex must NOT advance its epoch, so a resume re-attempts
        // exactly that vertex (and fails again here) rather than skipping past it.
        val execution = newExecution(errorDocumentPath)
        execution.beforeStart(argument("n", 6))

        val control = MutableLogicControl(true)
        val resourceScope = MutableLogicResourceScope()
        val graphDefinition = graphDefinition(errorDocumentPath)

        assertEquals(LogicResultPaused,
            execution.continueOrStart(control, resourceScope, graphDefinition))
        assertEquals(LogicResultPaused,
            execution.continueOrStart(control, resourceScope, graphDefinition))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun argument(name: String, value: Any?): TupleValue {
        return TupleValue(listOf(
            TupleComponentValue(TupleComponentName(name), value)))
    }


    private fun graphDefinition(documentPath: DocumentPath): GraphDefinition {
        return AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())
            .transitiveSuccessful
            .filterTransitive(documentPath)
    }


    private fun newExecution(documentPath: DocumentPath): LogicExecution {
        val mainLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
        return AutoTestUtils.liveLogicExecution(context, mainLocation, UnusedLogicHandle)
    }


    private fun logic(documentPath: DocumentPath): Logic {
        val mainLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
        val graphInstance = context.graphCreator.createGraph(
            graphDefinition(documentPath), context.graphEnvironment)
        return graphInstance[mainLocation]?.reference as? Logic
            ?: throw IllegalStateException("Flow logic not found: $mainLocation")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A Flow with only built-in vertices neither starts nor nests another logic, so the handle is unused.
    private object UnusedLogicHandle: LogicHandle {
        override fun start(
            logicRunExecutionId: LogicRunExecutionId,
            originalObjectLocation: ObjectLocation
        ): LogicExecutionFacade =
            error("nested logic should not start for this flow")
    }
}
