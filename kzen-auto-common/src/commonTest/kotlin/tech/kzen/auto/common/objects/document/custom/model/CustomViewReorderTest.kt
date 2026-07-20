package tech.kzen.auto.common.objects.document.custom.model

import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


/**
 * The Custom view hides the root `main` object, so a drag expressed in view indices is off by however many
 * hidden entries precede it. Each case asserts both the computed document position and the view order that
 * position actually produces (ShiftObjectCommand removes, then re-inserts at the index — simulated by [applyShift]).
 */
class CustomViewReorderTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val main = ObjectPath.parse("main")
    private val a = ObjectPath.parse("A")
    private val b = ObjectPath.parse("B")
    private val c = ObjectPath.parse("C")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun viewPathsHidesRootMain() {
        assertEquals(listOf(a, b, c), CustomViewReorder.viewPaths(listOf(main, a, b, c)))
        assertEquals(listOf(a, b, c), CustomViewReorder.viewPaths(listOf(a, main, b, c)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun moveDownPastOneSibling() {
        val allDocPaths = listOf(main, a, b, c)
        val shift = CustomViewReorder.dropShift(allDocPaths, sourceViewIndex = 0, newViewIndex = 1)!!

        assertEquals(a, shift.sourcePath)
        assertEquals(2, shift.newDocPosition)
        assertEquals(listOf(b, a, c), viewAfterShift(allDocPaths, shift))
    }


    @Test
    fun moveUpToStart() {
        val allDocPaths = listOf(main, a, b, c)
        val shift = CustomViewReorder.dropShift(allDocPaths, sourceViewIndex = 2, newViewIndex = 0)!!

        assertEquals(c, shift.sourcePath)
        assertEquals(listOf(c, a, b), viewAfterShift(allDocPaths, shift))
    }


    @Test
    fun moveToEndAnchorsPastLast() {
        val allDocPaths = listOf(main, a, b, c)
        val shift = CustomViewReorder.dropShift(allDocPaths, sourceViewIndex = 0, newViewIndex = 2)!!

        // No anchor after the move, so it lands at the last document slot
        assertEquals(allDocPaths.size - 1, shift.newDocPosition)
        assertEquals(listOf(b, c, a), viewAfterShift(allDocPaths, shift))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun hiddenMainBetweenSourceAndAnchorOffsetsDocPosition() {
        // The off-by-one this translation exists to prevent: naively reusing the view index would land A back
        // where it started, because `main` sits between it and its anchor.
        val allDocPaths = listOf(a, main, b, c)
        val shift = CustomViewReorder.dropShift(allDocPaths, sourceViewIndex = 0, newViewIndex = 1)!!

        assertEquals(2, shift.newDocPosition)
        assertEquals(listOf(b, a, c), viewAfterShift(allDocPaths, shift))
    }


    @Test
    fun hiddenMainLastStillAnchors() {
        val allDocPaths = listOf(a, b, c, main)
        val shift = CustomViewReorder.dropShift(allDocPaths, sourceViewIndex = 2, newViewIndex = 1)!!

        assertEquals(c, shift.sourcePath)
        assertEquals(listOf(a, c, b), viewAfterShift(allDocPaths, shift))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun noOpMoveIsNull() {
        assertNull(CustomViewReorder.dropShift(listOf(main, a, b, c), sourceViewIndex = 1, newViewIndex = 1))
    }


    @Test
    fun outOfRangeIndicesAreNull() {
        val allDocPaths = listOf(main, a, b, c)
        assertNull(CustomViewReorder.dropShift(allDocPaths, sourceViewIndex = 3, newViewIndex = 0))
        assertNull(CustomViewReorder.dropShift(allDocPaths, sourceViewIndex = 0, newViewIndex = 3))
        assertNull(CustomViewReorder.dropShift(allDocPaths, sourceViewIndex = -1, newViewIndex = 0))
    }


    @Test
    fun singleViewEntryCannotMove() {
        assertNull(CustomViewReorder.dropShift(listOf(main, a), sourceViewIndex = 0, newViewIndex = 0))
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** Mirrors ShiftObjectCommand: remove the object, then insert it at the resolved document position. */
    private fun applyShift(allDocPaths: List<ObjectPath>, shift: CustomViewReorder.DropShift): List<ObjectPath> {
        val remaining = allDocPaths.toMutableList()
        remaining.remove(shift.sourcePath)
        remaining.add(shift.newDocPosition, shift.sourcePath)
        return remaining
    }


    private fun viewAfterShift(
        allDocPaths: List<ObjectPath>,
        shift: CustomViewReorder.DropShift
    ): List<ObjectPath> {
        return CustomViewReorder.viewPaths(applyShift(allDocPaths, shift))
    }
}
