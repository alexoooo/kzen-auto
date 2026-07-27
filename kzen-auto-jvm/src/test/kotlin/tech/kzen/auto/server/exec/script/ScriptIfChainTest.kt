package tech.kzen.auto.server.exec.script

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.script.test.CountingStep
import tech.kzen.auto.server.exec.script.test.ScriptStepTestModule
import tech.kzen.auto.server.objects.script.ScriptValidator
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


/**
 * The If chain (if / else-if / ... / else) end to end: which section runs, and what type the If contributes.
 *
 * Two things distinguish a chain from the former two-way then/else and are what these tests pin. FIRST-TRUE-WINS
 * IS LAZY — the engine stops at the first holding condition and never evaluates or runs a later branch whose
 * condition also holds — and the TYPE JOIN FOLDS over N+1 terminals (every condition branch plus the else)
 * rather than combining exactly two, so one divergent section widens the whole If to Any and one valueless
 * section makes it Unit.
 */
class ScriptIfChainTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val chainPath = DocumentPath.parse("test/script-engine-elseif-test.yaml")
    private val noElsePath = DocumentPath.parse("test/script-engine-if-no-else-test.yaml")
    private val uniformTypePath = DocumentPath.parse("test/if-step-type-test.yaml")
    private val divergentTypePath = DocumentPath.parse("test/if-step-divergent-type-test.yaml")

    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //------------------------------------------------------------------------------------------------- execution
    @Test
    fun theFirstHoldingConditionWinsOverALaterOneThatAlsoHolds() {
        // n = 1 satisfies branch 1 (`n == 1`) AND branch 3 (`n > 0`). Branch 3's CountingStep proves the chain
        // stopped at the first match rather than running every true branch.
        val outcome = runChain(1)
        assertEquals("one", assertIs<Outcome.Success>(outcome).value.mainComponentValue())
        assertEquals(0, CountingStep.count.get())
    }


    @Test
    fun aMiddleBranchRunsWhenItsConditionIsTheFirstToHold() {
        val outcome = runChain(2)
        assertEquals("two", assertIs<Outcome.Success>(outcome).value.mainComponentValue())
        assertEquals(0, CountingStep.count.get())
    }


    @Test
    fun aLaterBranchRunsOnceTheEarlierConditionsAreFalse() {
        val outcome = runChain(3)
        assertEquals("three", assertIs<Outcome.Success>(outcome).value.mainComponentValue())
        assertEquals(1, CountingStep.count.get())
    }


    @Test
    fun everyConditionFalseFallsThroughToTheElse() {
        val outcome = runChain(0)
        assertEquals("other", assertIs<Outcome.Success>(outcome).value.mainComponentValue())
        assertEquals(0, CountingStep.count.get())
    }


    @Test
    fun everyConditionFalseWithNoElseContributesNothingAndTheScriptProceeds() {
        // No else section at all: the If runs an empty step list, so it yields no value and the run continues
        // to After (7) — the Script must not stall or fail on a chain that matched nothing.
        val outcome = runScript(noElsePath)
        assertEquals(7, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
        assertEquals(0, CountingStep.count.get())
    }


    //------------------------------------------------------------------------------------------------ type join
    @Test
    fun uniformSectionsGiveTheIfTheirPreciseType() {
        assertEquals(
            TypeMetadata.string,
            ifTypeOf(uniformTypePath, "main.steps/Branch"),
            "two String branches and a String else fold to String")
    }


    @Test
    fun oneDivergentSectionWidensTheIfToAny() {
        assertEquals(
            TypeMetadata.any,
            ifTypeOf(divergentTypePath, "main.steps/Branch"),
            "an Int branch among String ones leaves only Any as the common supertype")
    }


    @Test
    fun anEmptyElseMakesTheWholeIfUnit() {
        // Unit dominates the fold: with no else the If may produce nothing, so it is not a dependable value
        // even though its one branch terminal is an Int.
        assertEquals(
            TypeMetadata.unit,
            ifTypeOf(noElsePath, "main.steps/Chain"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun runChain(n: Int): Outcome {
        return runScript(
            chainPath,
            TupleValue(listOf(TupleComponentValue(TupleComponentName("n"), n))))
    }


    private fun runScript(documentPath: DocumentPath, inputs: TupleValue = TupleValue.empty): Outcome {
        ScriptStepTestModule.register()
        CountingStep.reset()

        context = KzenAutoContext.forTest()

        val scriptLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val logic = ScriptLogicCompiler.compile(
            scriptLocation,
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


    private fun ifTypeOf(documentPath: DocumentPath, objectPath: String): TypeMetadata? {
        return validationOf(documentPath)
            .stepValidations[ObjectPath.parse(objectPath)]
            ?.typeMetadata
    }


    private fun validationOf(documentPath: DocumentPath): ScriptValidation {
        ScriptStepTestModule.register()
        context = KzenAutoContext.forTest()

        val graphNotation = AutoTestUtils.readNotation()
        val stepGraphDefinition = AutoTestUtils
            .graphDefinitionAttempt(graphNotation)
            .transitiveSuccessful
            .filterTransitive(documentPath)

        val graphInstance = GraphCreator.createGraph(stepGraphDefinition, context.graphEnvironment)

        return ScriptValidator.validate(documentPath, graphNotation, stepGraphDefinition, graphInstance)
    }
}
