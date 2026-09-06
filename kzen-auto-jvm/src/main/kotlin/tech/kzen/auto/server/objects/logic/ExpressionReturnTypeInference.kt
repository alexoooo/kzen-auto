package tech.kzen.auto.server.objects.logic

import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import java.util.stream.Stream


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
 * A classifier is exposed as its concrete [ClassName] only when generated Kotlin can name it: it has a
 * qualified name, public visibility, and is not synthetic. The reflected [KType] came from an already-loaded
 * expression class, so loadability is given; testing it again would incorrectly reject mapped Kotlin types
 * such as `kotlin.Int`. Anything unnameable approximates to `Any` (nullability preserved), and a generic
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

    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The raw [KType] the compiler inferred for [clazz]'s probe expression, BEFORE any visibility
     * approximation — the form the strict-static stream-vs-single classification ([isStreamType] /
     * [streamElementType]) needs, since approximation can erase stream-ness for an unnameable subtype.
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


    fun inferReturnType(clazz: Class<out Any>): TypeMetadata {
        return toTypeMetadata(inferReturnKType(clazz))
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The strict-static stream classification: an `Iterable`, `Sequence`, `Iterator` or `java.util.stream.Stream`
     * expression type streams its elements (per [streamElementType]); anything else — including `Any` — emits
     * a single message. The inferred type alone decides; the runtime value never does.
     */
    fun isStreamType(kType: KType): Boolean {
        val classifier = kType.classifier as? KClass<*>
            ?: return false
        return streamClassifiers.any { classifier.isSubclassOf(it) }
    }


    /**
     * The element type of an [isStreamType]-classified [kType], projected in fixed `Iterable`, `Sequence`,
     * `Iterator` order: `List<T>` → `T` (a directly-substituted argument), `IntRange` → `Int` (a concrete
     * supertype argument), and a generic custom stream resolves one level of type-parameter indirection. Null
     * when the element cannot be resolved (deeper indirection, star projection) — the caller approximates to
     * Any.
     */
    fun streamElementType(kType: KType): KType? {
        val classifier = kType.classifier as? KClass<*>
            ?: return null
        if (classifier in streamClassifiers) {
            return kType.arguments.singleOrNull()?.type
        }

        val streamSupertype = streamClassifiers.firstNotNullOfOrNull { streamClassifier ->
            classifier.allSupertypes.firstOrNull { it.classifier == streamClassifier }
        }
            ?: return null
        val element = streamSupertype.arguments.singleOrNull()?.type
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


    /**
     * Converts a statically stream-classified expression value into the iterator both runtimes consume. A
     * `Stream`'s iterator does not close the stream: the container is closed separately (E9 item 1).
     */
    fun streamIterator(value: Any?): Iterator<*>? {
        return when (value) {
            is Iterable<*> -> value.iterator()
            is Sequence<*> -> value.iterator()
            is Iterator<*> -> value
            is Stream<*> -> value.iterator()
            else -> null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toTypeMetadata(kType: KType): TypeMetadata {
        val classifier = kType.classifier as? KClass<*>
        if (classifier == null || !isNameable(classifier)) {
            // A type variable / star projection with no concrete classifier.
            return TypeMetadata(ClassNames.kotlinAny, listOf(), kType.isMarkedNullable)
        }

        val className = ClassName(classifier.qualifiedName!!)

        val generics = kType.arguments.map {
            it.type?.let(::toTypeMetadata) ?: TypeMetadata.any
        }

        return TypeMetadata(className, generics, kType.isMarkedNullable)
    }


    private fun isNameable(kClass: KClass<*>): Boolean {
        return kClass.qualifiedName != null &&
                kClass.visibility == KVisibility.PUBLIC &&
                !kClass.java.isSynthetic
    }


    private val streamClassifiers = listOf(Iterable::class, Sequence::class, Iterator::class, Stream::class)
}
