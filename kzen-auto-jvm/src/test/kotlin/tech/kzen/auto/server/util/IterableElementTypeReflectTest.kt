package tech.kzen.auto.server.util

import org.junit.Test
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames
import kotlin.test.assertEquals
import kotlin.test.assertNull


/**
 * The element type is recovered generally from each class's Iterable<E> supertype — one mechanism, not a
 * per-type map. IntRange is the case driving the feature (see ForEachItemBindingTest); LongRange/CharRange
 * demonstrate the generality (they aren't reachable end-to-end yet, as FormulaStep only recognizes IntRange).
 */
class IterableElementTypeReflectTest {
    @Test
    fun intRangeElementIsInt() {
        assertEquals(
            TypeMetadata(ClassNames.kotlinInt, listOf(), false),
            IterableElementTypeReflect.elementType(ClassName("kotlin.ranges.IntRange")))
    }


    @Test
    fun longRangeElementIsLong() {
        assertEquals(
            TypeMetadata(ClassNames.kotlinLong, listOf(), false),
            IterableElementTypeReflect.elementType(ClassName("kotlin.ranges.LongRange")))
    }


    @Test
    fun charRangeElementIsChar() {
        assertEquals(
            TypeMetadata(ClassName("kotlin.Char"), listOf(), false),
            IterableElementTypeReflect.elementType(ClassName("kotlin.ranges.CharRange")))
    }


    @Test
    fun nonIterableHasNoElementType() {
        assertNull(IterableElementTypeReflect.elementType(ClassName("java.lang.Object")))
    }
}
