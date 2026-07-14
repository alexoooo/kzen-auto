package tech.kzen.auto.server.objects.script.step.eval

import tech.kzen.auto.common.objects.document.registry.model.ObjectRegistryScan
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberProperties


/**
 * Recovers a [FormulaStep]'s value type by reflecting the type the compiler inferred for the user's
 * expression: `StepExpressionCompiler.generateInferenceCode` emits it as a lambda-valued property
 * ([StepExpressionCompiler.probePropertyName], type `() -> T`), whose single type argument is the
 * expression's type — so the type comes from the Kotlin compiler's own inference, not from parsing
 * diagnostic text. (A property holding a lambda rather than a plain inferred member so a `Nothing`-typed
 * expression compiles — see the probe's doc in StepExpressionCompiler.)
 *
 * A classifier is exposed as its concrete [ClassName] only when it is "visible": a known-importable builtin
 * ([visibleBuiltins]) or a type the registry scan declares. Anything else — an internal / synthetic type a
 * downstream expression could not import — approximates to `Any` (nullability preserved), and a generic
 * argument that is a star projection or otherwise unresolved approximates to [TypeMetadata.any]. A flexible /
 * platform type resolves to whichever denotable classifier reflection reports.
 */
object StepReturnTypeInference {
    // The builtins a generated expression can safely name / import when a downstream step references this
    // value by its inferred type. Matched by full ClassName so there is no simple-name collision with a
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
        // `1..100` infers to IntRange; recognize it so the step (and a ForEach iterating over it) is typed.
        ClassName("kotlin.ranges.IntRange"))


    fun inferReturnType(clazz: Class<out Any>, objectRegistryScan: ObjectRegistryScan): TypeMetadata {
        val probe = clazz.kotlin.declaredMemberProperties
            .first { it.name == StepExpressionCompiler.probePropertyName }

        // The probe's type is Function0<T>; its single argument is the expression's inferred type. The null
        // branch (a star projection with no type) cannot occur for an inferred lambda, but approximates to
        // Any rather than crashing.
        val valueType = probe.returnType.arguments.single().type
        return valueType?.toTypeMetadata(objectRegistryScan)
            ?: TypeMetadata(ClassNames.kotlinAny, listOf(), false)
    }


    private fun KType.toTypeMetadata(objectRegistryScan: ObjectRegistryScan): TypeMetadata {
        val qualifiedName = (classifier as? KClass<*>)?.qualifiedName
        if (qualifiedName == null) {
            // A type variable / star projection with no concrete classifier.
            return TypeMetadata(ClassNames.kotlinAny, listOf(), isMarkedNullable)
        }

        val className = ClassName(qualifiedName)
        if (className !in visibleBuiltins && className !in objectRegistryScan.classNames) {
            return TypeMetadata(ClassNames.kotlinAny, listOf(), isMarkedNullable)
        }

        val generics = arguments.map {
            it.type?.toTypeMetadata(objectRegistryScan) ?: TypeMetadata.any
        }

        return TypeMetadata(className, generics, isMarkedNullable)
    }
}
