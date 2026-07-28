package tech.kzen.auto.server.objects.logic

import tech.kzen.auto.common.objects.document.registry.model.ObjectRegistryScan
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames
import java.util.UUID
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * The reflective type contract shared by every inference-mode expression: `ForEachStep`'s items (is it
 * iterable, and over what), the Job/Report `FormulaSourceWorker`'s strict-static stream-vs-single decision,
 * and `FormulaStep`'s reported value type.
 *
 * These run against [kotlin.reflect.typeOf] rather than a compiled expression class, because the interesting
 * cases are all about the SHAPE of the KType (a concrete Iterable supertype argument, a substituted type
 * parameter, a star projection) — the probe-property plumbing that produces it is exercised end to end by
 * FormulaStepTest and ForEachItemsTest.
 */
class ExpressionReturnTypeInferenceTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val emptyScan = ObjectRegistryScan(setOf())


    //-------------------------------------------------------------------------------------------- classification
    @Test
    fun aRangeIsIterable() {
        assertTrue(ExpressionReturnTypeInference.isIterable(typeOf<IntRange>()))
        assertTrue(ExpressionReturnTypeInference.isIterable(typeOf<List<String>>()))
        assertTrue(ExpressionReturnTypeInference.isIterable(typeOf<Iterable<Int>>()))
    }


    @Test
    fun aSequenceIsNotIterable() {
        // Sequence deliberately does NOT extend Iterable — a one-shot pipeline is a different contract, and
        // a ForEach over one would silently consume it. `.asIterable()` is the explicit opt-in.
        assertFalse(ExpressionReturnTypeInference.isIterable(typeOf<Sequence<Int>>()))
    }


    @Test
    fun anUninformativeTypeIsNotIterable() {
        // `Any` carries no iterability information at all — callers must treat "not iterable" here as
        // "unknown", not as "definitely not" (see ForEachItemsExpression's three-way classification).
        assertFalse(ExpressionReturnTypeInference.isIterable(typeOf<Any>()))
        assertFalse(ExpressionReturnTypeInference.isIterable(typeOf<Int>()))
    }


    //-------------------------------------------------------------------------------------------- element type
    @Test
    fun aRangeFixesItsElementTypeInItsSupertype() {
        // IntRange exposes no generic parameter of its own; Int comes from `IntRange : Iterable<Int>`.
        assertEquals(typeOf<Int>(), ExpressionReturnTypeInference.iterableElementType(typeOf<IntRange>()))
        assertEquals(typeOf<Long>(), ExpressionReturnTypeInference.iterableElementType(typeOf<LongRange>()))
        assertEquals(typeOf<Char>(), ExpressionReturnTypeInference.iterableElementType(typeOf<CharRange>()))
    }


    @Test
    fun aParameterizedCollectionSubstitutesItsOwnArgument() {
        // List<E> declares Iterable<E>, so the element resolves through one level of type-parameter
        // indirection back to the instance's same-index argument.
        assertEquals(
            typeOf<String>(),
            ExpressionReturnTypeInference.iterableElementType(typeOf<List<String>>()))
        assertEquals(
            typeOf<String?>(),
            ExpressionReturnTypeInference.iterableElementType(typeOf<List<String?>>()))
        assertEquals(
            typeOf<Int>(),
            ExpressionReturnTypeInference.iterableElementType(typeOf<Set<Int>>()))
    }


    @Test
    fun anIterableIsItsOwnArgument() {
        assertEquals(
            typeOf<Int>(),
            ExpressionReturnTypeInference.iterableElementType(typeOf<Iterable<Int>>()))
    }


    @Test
    fun aStarProjectionLeavesTheElementUnresolved() {
        // Null means "approximate to Any" for the caller, not "empty".
        assertNull(ExpressionReturnTypeInference.iterableElementType(typeOf<List<*>>()))
    }


    @Test
    fun aNonIterableHasNoElementType() {
        assertNull(ExpressionReturnTypeInference.iterableElementType(typeOf<Int>()))
    }


    //-------------------------------------------------------------------------------------------- visibility
    @Test
    fun iterableIsAVisibleBuiltin() {
        // Not a formality: without it `xs.asSequence().asIterable()` approximates to Any, and a ForEach over
        // it loses the element type its loop item is supposed to publish.
        assertEquals(
            TypeMetadata(ClassName("kotlin.collections.Iterable"), listOf(TypeMetadata.int), false),
            ExpressionReturnTypeInference.toTypeMetadata(typeOf<Iterable<Int>>(), emptyScan))
    }


    @Test
    fun aTypeOutsideTheVisibleSetApproximatesToAnyPreservingNullability() {
        // An expression could not import it downstream, so exposing the concrete name would be a lie.
        assertEquals(
            TypeMetadata.any,
            ExpressionReturnTypeInference.toTypeMetadata(typeOf<UUID>(), emptyScan))
        assertEquals(
            TypeMetadata.anyNullable,
            ExpressionReturnTypeInference.toTypeMetadata(typeOf<UUID?>(), emptyScan))
    }


    @Test
    fun aRegistryDeclaredTypeStaysConcrete() {
        val scan = ObjectRegistryScan(setOf(ClassName("java.util.UUID")))
        assertEquals(
            TypeMetadata(ClassName("java.util.UUID"), listOf(), false),
            ExpressionReturnTypeInference.toTypeMetadata(typeOf<UUID>(), scan))
    }


    @Test
    fun aVisibleBuiltinsGenericsAreRecursivelyApproximated() {
        assertEquals(
            TypeMetadata(ClassNames.kotlinList, listOf(TypeMetadata.any), false),
            ExpressionReturnTypeInference.toTypeMetadata(typeOf<List<UUID>>(), emptyScan))
    }
}
