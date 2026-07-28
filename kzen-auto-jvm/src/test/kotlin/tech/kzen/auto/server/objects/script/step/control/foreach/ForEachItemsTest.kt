package tech.kzen.auto.server.objects.script.step.control.foreach

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.script.ScriptLogicCompiler
import tech.kzen.auto.server.exec.script.test.ScriptStepTestModule
import tech.kzen.auto.server.objects.script.ScriptValidator
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * A ForEach's `items` as a Kotlin EXPRESSION rather than a reference to a collection-producing step.
 *
 * Two things distinguish it from the other expression attributes and are what these tests pin. The
 * expression is compiled in the INFERENCE form, so its inferred ELEMENT type — not the expression's own
 * type — is what the loop's `item` binding publishes to the body (a forced `Iterable<*>` return would
 * compile identically and erase it). And iterability is judged THREE ways, not two: an inferred type that
 * is definitely not an Iterable is the loop's validation error, but one that carries no information (`Any`,
 * `Nothing`) is accepted and left to the run-time cast, because rejecting it would break scripts that work.
 */
class ForEachItemsTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val inlineRangePath = DocumentPath.parse("test/foreach-item-range-test.yaml")
    private val bodyTypePath = DocumentPath.parse("test/foreach-body-type-test.yaml")
    private val listPath = DocumentPath.parse("test/foreach-items-list-test.yaml")
    private val nonIterablePath = DocumentPath.parse("test/foreach-items-non-iterable-test.yaml")
    private val unsetPath = DocumentPath.parse("test/foreach-items-unset-test.yaml")
    private val compileErrorPath = DocumentPath.parse("test/foreach-items-compile-error-test.yaml")
    private val opaquePath = DocumentPath.parse("test/foreach-items-opaque-test.yaml")
    private val outerItemPath = DocumentPath.parse("test/foreach-items-outer-item-test.yaml")
    private val selfReferencePath = DocumentPath.parse("test/foreach-items-self-reference-test.yaml")
    private val nonCollectionPath = DocumentPath.parse("test/script-loop-migration-noncollection-test.yaml")

    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //---------------------------------------------------------------------------------------------- element type
    @Test
    fun anInlineRangeNeedsNoUpstreamProducerStep() {
        // The whole point of the change: `1..3` written directly on the loop, with no FormulaStep anywhere in
        // the document to point at. IntRange exposes no generic parameter, so Int comes from projecting onto
        // its Iterable supertype.
        assertEquals(
            TypeMetadata.int,
            itemTypeOf(inlineRangePath),
            "the loop item is the element type of the inline range")
        assertNull(loopErrorOf(inlineRangePath))
    }


    @Test
    fun aParameterizedCollectionCarriesItsElementTypeIncludingNullability() {
        // A non-null String here would make the generated accessor's `as String` throw on the null element.
        assertEquals(
            TypeMetadata(ClassNames.kotlinString, listOf(), true),
            itemTypeOf(listPath))
    }


    @Test
    fun theLoopsOwnTypeIsItsBodyTerminalNotTheItemsElementType() {
        // The two are genuinely different and both must be right: the loop COLLECTS what its body returns.
        assertEquals(TypeMetadata.int, itemTypeOf(bodyTypePath))
        assertEquals(
            TypeMetadata(ClassNames.kotlinList, listOf(TypeMetadata.string), false),
            loopTypeOf(bodyTypePath),
            "iterating Ints while the body yields Strings makes the loop a List<String>")
    }


    @Test
    fun anAsIterableExpressionStillYieldsItsElementType() {
        // `listOf(...).asSequence().asIterable()` infers to Iterable<Int>, which only types precisely because
        // Iterable is a visible builtin (ExpressionReturnTypeInference.visibleBuiltins) — otherwise it would
        // approximate to Any and take the opaque path below, costing the item its Int.
        assertEquals(TypeMetadata.int, itemTypeOf(nonCollectionPath))
    }


    //---------------------------------------------------------------------------------------------- validation
    @Test
    fun aDefinitelyNonIterableExpressionIsTheLoopsError() {
        assertEquals("Items are not iterable: Int", loopErrorOf(nonIterablePath))
        assertNull(loopTypeOf(nonIterablePath), "a rejected loop contributes no type")
    }


    @Test
    fun aRejectedLoopStillLetsItsBodyValidateOnItsOwnTerms() {
        // The item binding publishes Any? instead of deferring, so the body reports its own type rather than
        // the "Unresolved: circular or unavailable dependency" backstop a permanent defer would produce.
        val validation = validationOf(nonIterablePath)
        assertEquals(
            TypeMetadata.anyNullable,
            validation.stepValidations[ObjectPath.parse("main.steps/Loop.item/Item")]?.typeMetadata)

        val body = validation.stepValidations[ObjectPath.parse("main.steps/Loop.steps/Body")]
        assertEquals(TypeMetadata.string, body?.typeMetadata)
        assertNull(body?.errorMessage, "the body's own expression is fine — only the loop is broken")
    }


    @Test
    fun anUnsetItemsExpressionReportsItemsNotSet() {
        // A freshly-inserted loop carries the archetype's empty default; that is called out explicitly rather
        // than left to the compiler's "Unit is not an Iterable".
        assertEquals("Items not set", loopErrorOf(unsetPath))
    }


    @Test
    fun aBrokenItemsExpressionIsTheLoopsErrorAndTheItemStillTypes() {
        val error = loopErrorOf(compileErrorPath)
        assertNotNull(error, "an unresolvable reference must not validate")
        assertTrue(
            error.contains("nowhere"),
            "the Kotlin compile error must name the offending identifier, was: $error")

        assertEquals(TypeMetadata.anyNullable, itemTypeOf(compileErrorPath))
    }


    @Test
    fun anOpaquelyTypedItemsExpressionIsAcceptedAndTypesTheItemAsAnyNullable() {
        // `as Any` erases iterability statically. Rejecting it would break a working script, so the loop
        // validates and the run-time cast decides.
        assertNull(loopErrorOf(opaquePath))
        assertEquals(TypeMetadata.anyNullable, itemTypeOf(opaquePath))
    }


    //---------------------------------------------------------------------------------------------------- scope
    @Test
    fun aLoopsOwnItemIsNotInScopeForItsOwnItemsExpression() {
        // Otherwise the item's type would be defined in terms of itself. It resolves to nothing at all —
        // an ordinary unresolved reference, not a validator cycle.
        val error = loopErrorOf(selfReferencePath)
        assertNotNull(error, "the loop's own item must not be nameable by its items expression")
        assertTrue(
            error.contains("Item"),
            "the error must name the unresolvable identifier, was: $error")
    }


    @Test
    fun anEnclosingLoopsItemIsInScopeForAnInnerLoopsItemsExpression() {
        val validation = validationOf(outerItemPath)
        assertNull(
            validation.stepValidations[ObjectPath.parse("main.steps/Outer.steps/Inner")]?.errorMessage,
            "the inner range may be a function of the outer element")
        assertEquals(
            TypeMetadata.int,
            validation
                .stepValidations[ObjectPath.parse("main.steps/Outer.steps/Inner.item/Inner Item")]
                ?.typeMetadata)
    }


    //------------------------------------------------------------------------------------------------ execution
    @Test
    fun aNestedLoopIteratesARangeDerivedFromTheOuterItem() {
        // 1 + (1+2) + (1+2+3) — proves the expression is evaluated at each loop ENTRY against the current
        // outer item, not once for the whole run.
        val outcome = runScript(outerItemPath)
        assertEquals(10, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
    }


    @Test
    fun anOpaquelyTypedItemsValueIteratesAtRunTime() {
        val outcome = runScript(opaquePath)
        assertEquals(12, assertIs<Outcome.Success>(outcome).value.mainComponentValue())
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun itemTypeOf(documentPath: DocumentPath): TypeMetadata? {
        return validationOf(documentPath).stepValidations[itemPath]?.typeMetadata
    }


    private fun loopTypeOf(documentPath: DocumentPath): TypeMetadata? {
        return validationOf(documentPath).stepValidations[loopPath]?.typeMetadata
    }


    private fun loopErrorOf(documentPath: DocumentPath): String? {
        return validationOf(documentPath).stepValidations[loopPath]?.errorMessage
    }


    private val loopPath = ObjectPath.parse("main.steps/Loop")
    private val itemPath = ObjectPath.parse("main.steps/Loop.item/Item")


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


    private fun runScript(documentPath: DocumentPath): Outcome {
        ScriptStepTestModule.register()
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

        val engine = RunEngine(
            logic, context.objectStableMapper.objectStableId(scriptLocation), TupleValue.empty)
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
