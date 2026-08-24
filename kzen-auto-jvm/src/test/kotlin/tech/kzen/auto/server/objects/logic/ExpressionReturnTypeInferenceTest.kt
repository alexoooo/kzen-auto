package tech.kzen.auto.server.objects.logic

import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames
import java.util.UUID
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * The reflective type contract shared by every inference-mode expression: `ForEachStep`'s items (is it a
 * stream, and over what), the Job/Report `FormulaSourceWorker`'s strict-static stream-vs-single decision,
 * and `FormulaStep`'s reported value type.
 *
 * These run against [kotlin.reflect.typeOf] rather than a compiled expression class, because the interesting
 * cases are all about the shape of the KType. The probe-property plumbing that produces it is exercised end
 * to end by FormulaStepTest and ForEachItemsTest.
 */
class ExpressionReturnTypeInferenceTest {
    //-------------------------------------------------------------------------------------------- classification
    @Test
    fun supportedStreamsAreClassifiedStatically() {
        assertTrue(ExpressionReturnTypeInference.isStreamType(typeOf<IntRange>()))
        assertTrue(ExpressionReturnTypeInference.isStreamType(typeOf<List<String>>()))
        assertTrue(ExpressionReturnTypeInference.isStreamType(typeOf<Iterable<Int>>()))
        assertTrue(ExpressionReturnTypeInference.isStreamType(typeOf<Sequence<Int>>()))
        assertTrue(ExpressionReturnTypeInference.isStreamType(typeOf<Iterator<Int>>()))
        assertTrue(ExpressionReturnTypeInference.isStreamType(typeOf<List<String>?>()))
    }


    @Test
    fun nonStreamsAreNotClassifiedAsStreams() {
        assertFalse(ExpressionReturnTypeInference.isStreamType(typeOf<Any>()))
        assertFalse(ExpressionReturnTypeInference.isStreamType(typeOf<String>()))
        assertFalse(ExpressionReturnTypeInference.isStreamType(typeOf<Int>()))
    }


    //-------------------------------------------------------------------------------------------- element type
    @Test
    fun aRangeFixesItsElementTypeInItsSupertype() {
        assertEquals(typeOf<Int>(), ExpressionReturnTypeInference.streamElementType(typeOf<IntRange>()))
        assertEquals(typeOf<Long>(), ExpressionReturnTypeInference.streamElementType(typeOf<LongRange>()))
        assertEquals(typeOf<Char>(), ExpressionReturnTypeInference.streamElementType(typeOf<CharRange>()))
    }


    @Test
    fun parameterizedStreamsSubstituteTheirOwnArgument() {
        assertEquals(
            typeOf<String>(),
            ExpressionReturnTypeInference.streamElementType(typeOf<List<String>>()))
        assertEquals(
            typeOf<String?>(),
            ExpressionReturnTypeInference.streamElementType(typeOf<List<String?>>()))
        assertEquals(
            typeOf<Int>(),
            ExpressionReturnTypeInference.streamElementType(typeOf<Set<Int>>()))
        assertEquals(
            typeOf<Long>(),
            ExpressionReturnTypeInference.streamElementType(typeOf<Sequence<Long>>()))
        assertEquals(
            typeOf<Double>(),
            ExpressionReturnTypeInference.streamElementType(typeOf<Iterator<Double>>()))
    }


    @Test
    fun aStarProjectionLeavesTheElementUnresolved() {
        assertNull(ExpressionReturnTypeInference.streamElementType(typeOf<List<*>>()))
    }


    @Test
    fun aNonStreamHasNoElementType() {
        assertNull(ExpressionReturnTypeInference.streamElementType(typeOf<Int>()))
    }


    @Test
    fun runtimeValuesShareOneIteratorConversion() {
        assertEquals(
            listOf(1, 2),
            ExpressionReturnTypeInference.streamIterator(listOf(1, 2))!!.asSequence().toList())
        assertEquals(
            listOf("a", "b"),
            ExpressionReturnTypeInference.streamIterator(sequenceOf("a", "b"))!!.asSequence().toList())
        assertEquals(
            listOf(3L, 4L),
            ExpressionReturnTypeInference.streamIterator(listOf(3L, 4L).iterator())!!.asSequence().toList())
        assertNull(ExpressionReturnTypeInference.streamIterator("not a stream"))
        assertNull(ExpressionReturnTypeInference.streamIterator(null))
    }


    //-------------------------------------------------------------------------------------------- visibility
    @Test
    fun mappedBuiltinsStayConcreteWithoutAWhitelist() {
        assertEquals(TypeMetadata.int, ExpressionReturnTypeInference.toTypeMetadata(typeOf<Int>()))
        assertEquals(TypeMetadata.string, ExpressionReturnTypeInference.toTypeMetadata(typeOf<String>()))
        assertEquals(
            TypeMetadata(ClassNames.kotlinList, listOf(TypeMetadata.string), false),
            ExpressionReturnTypeInference.toTypeMetadata(typeOf<List<String>>()))
        assertEquals(
            TypeMetadata(ClassName("kotlin.ranges.IntRange"), listOf(), false),
            ExpressionReturnTypeInference.toTypeMetadata(typeOf<IntRange>()))
    }


    @Test
    fun aFirstPartyPublicTypeStaysConcrete() {
        assertEquals(
            TypeMetadata(ClassName("tech.kzen.auto.common.data.model.DataUnit"), listOf(), false),
            ExpressionReturnTypeInference.toTypeMetadata(typeOf<DataUnit>()))
    }


    @Test
    fun aPublicJavaTypeStaysConcrete() {
        assertEquals(
            TypeMetadata(ClassName("java.util.UUID"), listOf(), false),
            ExpressionReturnTypeInference.toTypeMetadata(typeOf<UUID>()))
    }


    @Test
    fun anInternalTypeErasesToAnyPreservingNullability() {
        assertEquals(
            TypeMetadata.any,
            ExpressionReturnTypeInference.toTypeMetadata(typeOf<InternalInferenceValue>()))
        assertEquals(
            TypeMetadata.anyNullable,
            ExpressionReturnTypeInference.toTypeMetadata(typeOf<InternalInferenceValue?>()))
    }


    @Test
    fun anAnonymousTypeErasesToAny() {
        val anonymous = object {}
        assertEquals(
            TypeMetadata.any,
            ExpressionReturnTypeInference.toTypeMetadata(anonymous::class.createType()))
    }
}


internal class InternalInferenceValue
