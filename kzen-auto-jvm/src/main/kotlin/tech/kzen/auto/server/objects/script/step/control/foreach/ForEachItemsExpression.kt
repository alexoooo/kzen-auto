package tech.kzen.auto.server.objects.script.step.control.foreach

import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.server.objects.logic.ExpressionReturnTypeInference
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.binding.ForEachItemBinding
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.step.eval.StepExpressionSupport
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.platform.ClassNames
import kotlin.reflect.KClass
import kotlin.reflect.KType


/**
 * The `items` expression of a [ForEachStep]: the user Kotlin expression the loop iterates over, compiled in
 * the INFERENCE form ([StepExpressionSupport.generateInferenceCode]) so the compiler's own inferred type
 * yields the ELEMENT type — the loop variable's type. Forcing an `Iterable<*>` return would compile just as
 * well but erase exactly the thing the loop exists to publish.
 *
 * Two objects need that derivation and must not drift: [ForEachStep] (which validates the expression and
 * evaluates it at loop entry) and [ForEachItemBinding] (which publishes the element type to the body). The
 * binding cannot simply read the ForEach's validation, and no channel can be added to carry it either:
 * `ScriptValidator` records a step's definition EXACTLY ONCE, so for the ForEach to publish an element type
 * it would have to commit its own type in the same breath — but its type is `List<bodyTerminalType>`, and
 * the body terminal needs the binding's type. That is a cycle (ForEach -> body terminal -> binding ->
 * ForEach), which is why each derives the element type independently from this one place instead.
 *
 * The second derivation is close to free: [CachedKotlinCompiler] is keyed by content signature (class name +
 * source digest), and both callers pass the FOREACH's location, so both generate byte-identical source and
 * the second compile is a cache hit. The scope map is identical too — [StepExpressionSupport.resolveNonUnit]
 * returns null until every in-scope type is recorded, and recorded types are write-once, so the first
 * non-null map is already the final one.
 */
object ForEachItemsExpression {
    //-----------------------------------------------------------------------------------------------------------------
    sealed interface Attempt {
        /**
         * An in-scope type is not resolved yet — BOTH callers return null from `definition` and the
         * ScriptValidator iterates to a fixpoint. Neither may substitute a fallback type: a step is
         * recorded once, so an early `Any?` would permanently lose the precise element type.
         */
        data object Deferred: Attempt

        /**
         * The expression cannot be used. [ForEachStep] reports [error] as its validation error (it owns the
         * editor the user must fix); [ForEachItemBinding] reports NO error but still publishes a type, so
         * the body keeps validating and showing its own problems instead of a cascade of "Unresolved".
         */
        data class Invalid(val error: String): Attempt

        /** Usable; [elementType] is the loop variable's type. */
        data class Valid(val elementType: TypeMetadata): Attempt
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Read from notation rather than injected, for [ForEachItemBinding] — which holds only its enclosing
    // ForEach's location, not the ForEach's constructor arguments. Blank when unset (a fresh loop defaults
    // to ""), which is the same value the ForEach's own `items` parameter receives.
    fun code(forEachLocation: ObjectLocation, graphNotation: GraphNotation): String {
        return graphNotation
            .firstAttribute(forEachLocation, ScriptConventions.itemsAttributePath)
            ?.asString()
            ?: ""
    }


    /**
     * Compile the items expression and classify what it yields.
     *
     * [forEachLocation] is ALWAYS the ForEach, never the item binding: the generated class name derives from
     * it, and both callers' sources must be byte-identical for them to share one compile.
     */
    fun analyze(
        forEachLocation: ObjectLocation,
        code: String,
        scriptDefinitionContext: ScriptDefinitionContext,
        cachedKotlinCompiler: CachedKotlinCompiler
    ): Attempt {
        // Called out rather than left to the compiler: an empty expression compiles to a `Unit` body, whose
        // error would say nothing about what the user must actually do.
        if (code.isBlank()) {
            return Attempt.Invalid("Items not set")
        }

        val scope = StepExpressionSupport.resolveNonUnit(
            scopeTypes(forEachLocation, scriptDefinitionContext))
            ?: return Attempt.Deferred

        val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
        val generatedCode = StepExpressionSupport.generateInferenceCode(forEachLocation, code, scope)

        val compileError = cachedKotlinCompiler.tryCompile(generatedCode, classLoader)
        if (compileError != null) {
            return Attempt.Invalid(compileError)
        }

        val clazz = cachedKotlinCompiler.tryLoad(generatedCode, classLoader)
            ?: return Attempt.Invalid("Unable to load: $generatedCode")

        return classify(
            ExpressionReturnTypeInference.inferReturnKType(clazz),
            scriptDefinitionContext)
    }


    /**
     * Evaluate the items expression against the current in-scope values. Called once per loop ENTRY, not per
     * iteration — and through [StepExecution.perRunSingleton], so a loop nested in another loop reuses the
     * compiled instance rather than rebuilding it on each outer iteration.
     */
    fun evaluate(
        forEachLocation: ObjectLocation,
        code: String,
        execution: StepExecution,
        cachedKotlinCompiler: CachedKotlinCompiler
    ): Any? {
        check(code.isNotBlank()) {
            "ForEach items are not set: $forEachLocation"
        }

        val scope = StepExpressionSupport.resolveNonUnit(
            StepExpressionSupport.inScopeTypes(
                forEachLocation, execution.scriptTree, execution.scriptValidation))
            ?: error("Unresolved in-scope types for: $forEachLocation")

        return StepExpressionSupport.evaluate(
            forEachLocation, anyNullableReturnType, code, scope,
            { execution.referencedValue(it) }, cachedKotlinCompiler,
            infer = true,
            instanceCache = { signature, factory -> execution.perRunSingleton(signature, factory) })
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Unused by the inference codegen (which always declares `evaluate` as `Any?`), but the parameter is not
    // optional — named here so the call site reads as a deliberate "the type comes from inference".
    private const val anyNullableReturnType = "Any?"


    // The values the items expression can reference by name: the loop's own predecessors plus the parameters
    // / ENCLOSING loop items in scope. Note what this excludes and why it is correct: the loop's own body
    // steps are its children, not predecessors, and its own `item` binding is not in scope for the very
    // expression that determines it (ScriptTree.collectEnclosingItems stops AT the target before adding that
    // node's item branch) — so neither can be named here, and there is no cycle.
    private fun scopeTypes(
        forEachLocation: ObjectLocation,
        scriptDefinitionContext: ScriptDefinitionContext
    ) =
        StepExpressionSupport.inScopeTypes(
            forEachLocation,
            scriptDefinitionContext.scriptTree,
            scriptDefinitionContext.scriptValidation)


    /**
     * Three-way, not a boolean, because an inferred type can be genuinely uninformative:
     * [ExpressionReturnTypeInference.toTypeMetadata] approximates anything outside its visible set to `Any`,
     * and an expression can also infer to `Nothing` (`error(...)`) or to an unresolved classifier. Those say
     * NOTHING about iterability, so they are accepted and left to the run-time `as? Iterable<*>` backstop —
     * exactly the behaviour that held while `items` was a reference to a step of unknown type. Only a
     * classifier that is definitely not an Iterable (`Int`, `String`, `Sequence<T>`) is a validation error.
     */
    private fun classify(
        inferred: KType,
        scriptDefinitionContext: ScriptDefinitionContext
    ): Attempt {
        if (ExpressionReturnTypeInference.isIterable(inferred)) {
            val elementType = ExpressionReturnTypeInference
                .iterableElementType(inferred)
                ?.let {
                    ExpressionReturnTypeInference.toTypeMetadata(
                        it, scriptDefinitionContext.objectRegistryScan)
                }
                // An element the projection can't resolve (star projection, deeper type-parameter
                // indirection). NULLABLE Any deliberately: the generated accessor casts with `as <type>`,
                // so a non-null Any would throw on a collection that contains a null.
                ?: TypeMetadata.anyNullable

            return Attempt.Valid(elementType)
        }

        if (isOpaque(inferred)) {
            return Attempt.Valid(TypeMetadata.anyNullable)
        }

        return Attempt.Invalid("Items are not iterable: ${simpleDisplay(inferred)}")
    }


    // Whether the inferred type carries no usable information about iterability.
    private fun isOpaque(inferred: KType): Boolean {
        val classifier = inferred.classifier as? KClass<*>
            ?: return true

        val qualifiedName = classifier.qualifiedName
            ?: return true

        return qualifiedName == ClassNames.kotlinAny.asString() ||
                qualifiedName == nothingQualifiedName
    }


    private const val nothingQualifiedName = "kotlin.Nothing"


    // Rendered from the RAW inferred type, not from its TypeMetadata approximation — the approximation would
    // print "not iterable: Any" for a type this classifier already decided is definitely not Any.
    private fun simpleDisplay(inferred: KType): String {
        val simpleName = (inferred.classifier as? KClass<*>)?.simpleName
            ?: return "$inferred"

        return simpleName + if (inferred.isMarkedNullable) { "?" } else { "" }
    }
}
