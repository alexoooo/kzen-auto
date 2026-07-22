package tech.kzen.auto.server.objects.logic

import tech.kzen.auto.common.objects.document.registry.model.ObjectRegistryScan
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf


/**
 * Recovers a compiled user expression's value type by reflecting the type the compiler inferred for it —
 * the shared contract behind every inference-mode expression codegen (Script's `StepExpressionCompiler`,
 * the Job/Report `CalculatedColumnEval`): the generated class exposes the user's expression as a
 * lambda-valued property named [probePropertyName] (type `() -> T`, or `Receiver.() -> T` for a
 * receiver-scoped expression), whose LAST type argument is the expression's type — so the type comes from
 * the Kotlin compiler's own inference, not from parsing diagnostic text. (A property holding a lambda
 * rather than a plain inferred member so a `Nothing`-typed expression compiles — see the probe's doc in
 * StepExpressionCompiler.)
 *
 * A classifier is exposed as its concrete [ClassName] only when it is "visible": a known-importable builtin
 * ([visibleBuiltins]) or a type the registry scan declares. Anything else — an internal / synthetic type a
 * downstream expression could not import — approximates to `Any` (nullability preserved), and a generic
 * argument that is a star projection or otherwise unresolved approximates to [TypeMetadata.any]. A flexible /
 * platform type resolves to whichever denotable classifier reflection reports.
 */
object ExpressionReturnTypeInference {
    /**
     * The inference-mode member holding the user's expression as a lambda: the property's type is left to
     * the compiler to infer, so the expression's value type is recovered by reflecting the property type's
     * last argument. Owned here (the reflector) and referenced by the code generators
     * (StepExpressionCompiler, CalculatedColumnEval), so the contract cannot drift.
     */
    const val probePropertyName = "probe"


    // The builtins a generated expression can safely name / import when a downstream expression references
    // this value by its inferred type. Matched by full ClassName so there is no simple-name collision with a
    // registry type; anything outside this set (∪ the registry scan) approximates to Any.
    private val visibleBuiltins = setOf(
        ClassNames.kotlinUnit,
        ClassNames.kotlinAny,
        ClassNames.kotlinString,
        ClassNames.kotlinBoolean,
        ClassNames.kotlinInt,
        ClassNames.kotlinLong,
        ClassNames.kotlinDouble,
        ClassNames.kotlinList,
        ClassNames.kotlinSet,
        // `1..100` infers to IntRange; recognize it so the expression (and anything iterating over it) is typed.
        ClassName("kotlin.ranges.IntRange"))


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The raw [KType] the compiler inferred for [clazz]'s probe expression, BEFORE any visibility
     * approximation — the form the strict-static stream-vs-single classification ([isIterable] /
     * [iterableElementType]) needs, since approximation can erase Iterable-ness (a custom Iterable
     * subtype approximates to Any).
     */
    fun inferReturnKType(clazz: Class<out Any>): KType {
        val probe = clazz.kotlin.declaredMemberProperties
            .first { it.name == probePropertyName }

        // The probe's type is FunctionN (Function0 for a plain expression, Function1 for a receiver-scoped
        // one); the LAST argument is always the expression's inferred return type. An inferred lambda always
        // resolves it, so absence is a probe-contract violation rather than a user error.
        return probe.returnType.arguments.last().type
            ?: throw IllegalStateException("Probe return type unresolved: $clazz")
    }


    fun inferReturnType(clazz: Class<out Any>, objectRegistryScan: ObjectRegistryScan): TypeMetadata {
        return toTypeMetadata(inferReturnKType(clazz), objectRegistryScan)
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The strict-static stream classification: an `Iterable`-classified expression type streams (its
     * elements, per [iterableElementType]); anything else — including `Any` — emits a single message.
     * The inferred type alone decides; the runtime value never does.
     */
    fun isIterable(kType: KType): Boolean {
        val classifier = kType.classifier as? KClass<*>
            ?: return false
        return classifier.isSubclassOf(Iterable::class)
    }


    /**
     * The element type of an [isIterable]-classified [kType], projected onto its `Iterable` supertype:
     * `List<T>` → `T` (a directly-substituted argument), `IntRange` → `Int` (a concrete supertype
     * argument), a generic custom Iterable resolves one level of type-parameter indirection. Null when the
     * element cannot be resolved (deeper indirection, star projection) — the caller approximates to Any.
     */
    fun iterableElementType(kType: KType): KType? {
        val classifier = kType.classifier as? KClass<*>
            ?: return null
        if (classifier == Iterable::class) {
            return kType.arguments.singleOrNull()?.type
        }

        val iterableSupertype = classifier.allSupertypes
            .firstOrNull { it.classifier == Iterable::class }
            ?: return null
        val element = iterableSupertype.arguments.singleOrNull()?.type
            ?: return null

        // Concrete already (IntRange declares Iterable<Int>), or one level of type-parameter substitution
        // (List<E> declares Iterable<E>, E := the instance's same-index argument).
        val elementParameter = element.classifier as? KTypeParameter
            ?: return element
        val parameterIndex = classifier.typeParameters.indexOfFirst { it.name == elementParameter.name }
        if (parameterIndex == -1 || parameterIndex >= kType.arguments.size) {
            return null
        }
        return kType.arguments[parameterIndex].type
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toTypeMetadata(kType: KType, objectRegistryScan: ObjectRegistryScan): TypeMetadata {
        val qualifiedName = (kType.classifier as? KClass<*>)?.qualifiedName
        if (qualifiedName == null) {
            // A type variable / star projection with no concrete classifier.
            return TypeMetadata(ClassNames.kotlinAny, listOf(), kType.isMarkedNullable)
        }

        val className = ClassName(qualifiedName)
        if (className !in visibleBuiltins && className !in objectRegistryScan.classNames) {
            return TypeMetadata(ClassNames.kotlinAny, listOf(), kType.isMarkedNullable)
        }

        val generics = kType.arguments.map {
            it.type?.let { type -> toTypeMetadata(type, objectRegistryScan) } ?: TypeMetadata.any
        }

        return TypeMetadata(className, generics, kType.isMarkedNullable)
    }
}
