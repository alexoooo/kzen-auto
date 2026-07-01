package tech.kzen.auto.server.exec.script

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.script.test.ResourceDisposalLog
import tech.kzen.auto.server.exec.script.test.ScriptStepTestModule
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


/**
 * The extensibility acceptance test for the Script step redesign, and the companion flavour-dispatch fix (A4/A5):
 *
 * - [thirdPartyStepRunsWithNoCompilerChange] runs a Script containing [ShoutStep][tech.kzen.auto.server.exec.script.test.ShoutStep]
 *   — a step type defined ENTIRELY in the test source set and registered by hand ([ScriptStepTestModule]), with
 *   no `@Reflect`, no entry in [ScriptLogicCompiler] and no kzen `when`. It compiles through [LogicCompiler]
 *   (which resolves the Script archetype polymorphically as a [LogicDocument][tech.kzen.auto.server.exec.LogicDocument])
 *   and runs on the real [RunEngine]. If a third-party step needs no kzen edit to run, the step set is genuinely
 *   extensible — the defect the redesign set out to fix.
 *
 * - [resourcesDisposedPerClosePolicyOnSuccess] / [keepOnFailureResourceRetainedOnFailure] check the run-scoped
 *   resource registry ([StepExecution.openResource][tech.kzen.auto.server.objects.script.api.StepExecution.openResource])
 *   disposes each resource per its [ResourceClosePolicy][tech.kzen.lib.common.exec.logic.ResourceClosePolicy] when
 *   the run settles: Auto always, Manual never (auto), KeepOnFailure only when the run did not fail.
 *
 * The tests share the process-global [ResourceDisposalLog] and reset it per run, so they rely on the suite's
 * sequential execution (as the other static-fixture engine tests do).
 */
class ScriptExtensibilityTest {
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
    fun thirdPartyStepRunsWithNoCompilerChange() {
        val outcome = runScript("test/script-extensibility-test.yaml")
        assertEquals("HELLO!!!", assertIs<Outcome.Success>(outcome).value.mainComponentValue())
    }


    @Test
    fun resourcesDisposedPerClosePolicyOnSuccess() {
        val outcome = runScript("test/script-resource-success-test.yaml")
        assertIs<Outcome.Success>(outcome)
        assertEquals(setOf("auto", "keep"), ResourceDisposalLog.disposed())
    }


    @Test
    fun keepOnFailureResourceRetainedOnFailure() {
        val outcome = runScript("test/script-resource-failure-test.yaml")
        assertIs<Outcome.Failed>(outcome)
        assertEquals(setOf("auto"), ResourceDisposalLog.disposed())
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun runScript(documentPathString: String, inputs: TupleValue = TupleValue.empty): Outcome {
        ScriptStepTestModule.register()
        ResourceDisposalLog.reset()

        context = KzenAutoContext.forTest()

        val documentPath = DocumentPath.parse(documentPathString)
        val scriptLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val logic = LogicCompiler.compile(
            scriptLocation,
            graphNotation,
            graphDefinition,
            LogicCompilerServices(
                context.graphEnvironment,
                context.objectStableMapper,
                context.cachedKotlinCompiler,
                context.flowMessageInspector,
                context.notationMetadataReader,
                LogicRunExecutionId.random()))

        val engine = RunEngine(logic, context.objectStableMapper.objectStableId(scriptLocation), inputs)
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
}
