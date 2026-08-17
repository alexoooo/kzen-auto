package tech.kzen.auto.common.objects.document.common

import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ObjectSubtreeRemovalTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val main = ObjectPath.parse("main")

    private val stepFirst = ObjectPath.parse("main.steps/First")

    private val stepLoop = ObjectPath.parse("main.steps/Loop")
    private val loopInner = ObjectPath.parse("main.steps/Loop.steps/Inner")
    private val loopInnerDeep = ObjectPath.parse("main.steps/Loop.steps/Inner.steps/Deep")
    private val loopSecond = ObjectPath.parse("main.steps/Loop.steps/Second")

    // A sibling whose name has the subtree root's name as a text prefix.
    private val stepLoopTwo = ObjectPath.parse("main.steps/LoopTwo")
    private val loopTwoInner = ObjectPath.parse("main.steps/LoopTwo.steps/Inner")

    private val document = listOf(
        main, stepFirst, stepLoop, loopInner, loopInnerDeep, loopSecond, stepLoopTwo, loopTwoInner)


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun leafRemovesOnlyItself() {
        assertEquals(
            listOf(stepFirst),
            ObjectSubtreeRemoval.deepestFirst(document, stepFirst))
    }


    @Test
    fun rootIsRemovedLast() {
        val removal = ObjectSubtreeRemoval.deepestFirst(document, stepLoop)
        assertEquals(stepLoop, removal.last())
    }


    @Test
    fun deepestGoesFirst() {
        assertEquals(
            listOf(loopInnerDeep, loopInner, loopSecond, stepLoop),
            ObjectSubtreeRemoval.deepestFirst(document, stepLoop))
    }


    @Test
    fun everyObjectIsALeafWhenItsTurnComes() {
        val removal = ObjectSubtreeRemoval.deepestFirst(document, stepLoop)

        val pending = removal.toMutableList()
        for (objectPath in removal) {
            pending.remove(objectPath)
            assertTrue(
                pending.none { it.startsWith(objectPath) },
                "Not a leaf when removed: $objectPath")
        }
    }


    @Test
    fun sameDepthKeepsDocumentOrder() {
        val removal = ObjectSubtreeRemoval.deepestFirst(document, stepLoop)
        assertEquals(
            listOf(loopInner, loopSecond),
            removal.filter { it.nesting.segments.size == loopInner.nesting.segments.size })
    }


    @Test
    fun siblingWithNamePrefixIsNotIncluded() {
        val removal = ObjectSubtreeRemoval.deepestFirst(document, stepLoop)
        assertEquals(
            listOf(),
            removal.filter { it == stepLoopTwo || it == loopTwoInner })
    }


    @Test
    fun unrelatedObjectsAreNotIncluded() {
        assertEquals(
            listOf(loopTwoInner, stepLoopTwo),
            ObjectSubtreeRemoval.deepestFirst(document, stepLoopTwo))
    }


    @Test
    fun wholeDocumentIsRemovableFromMain() {
        val removal = ObjectSubtreeRemoval.deepestFirst(document, main)
        assertEquals(document.size, removal.size)
        assertEquals(main, removal.last())
    }


    @Test
    fun absentRootRemovesNothing() {
        assertEquals(
            listOf(),
            ObjectSubtreeRemoval.deepestFirst(listOf(main, stepFirst), stepLoop))
    }
}
