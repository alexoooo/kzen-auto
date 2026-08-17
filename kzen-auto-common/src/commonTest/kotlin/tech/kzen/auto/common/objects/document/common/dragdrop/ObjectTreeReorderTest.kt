package tech.kzen.auto.common.objects.document.common.dragdrop

import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull


/**
 * The row list a drag is expressed in is a projection of the document's object order, so every case asserts both
 * the computed document position and the order that position actually produces — [applyShift] mirrors what
 * `ShiftObjectTreeCommand` does (lift the whole subtree out, re-insert it contiguously at the resolved index).
 */
class ObjectTreeReorderTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val main = ObjectPath.parse("main")

    // A flat document: three rows and nothing else.
    private val workerA = ObjectPath.parse("main.workers/A")
    private val workerB = ObjectPath.parse("main.workers/B")
    private val workerC = ObjectPath.parse("main.workers/C")
    private val flatDocument = listOf(main, workerA, workerB, workerC)
    private val flatRows = listOf(workerA, workerB, workerC)

    // A document whose rows are interleaved with objects the row list doesn't show.
    private val paramOne = ObjectPath.parse("main.parameters/One")
    private val paramTwo = ObjectPath.parse("main.parameters/Two")
    private val paramThree = ObjectPath.parse("main.parameters/Three")
    private val unrelatedStep = ObjectPath.parse("main.steps/Unrelated")
    private val interleavedDocument = listOf(main, paramOne, paramTwo, unrelatedStep, paramThree)
    private val interleavedRows = listOf(paramOne, paramTwo, paramThree)

    // A document whose rows carry nested subtrees of their own.
    private val stepFirst = ObjectPath.parse("main.steps/First")
    private val stepLoop = ObjectPath.parse("main.steps/Loop")
    private val loopInner = ObjectPath.parse("main.steps/Loop.steps/Inner")
    private val loopInnerDeep = ObjectPath.parse("main.steps/Loop.steps/Inner.steps/Deep")
    private val stepLast = ObjectPath.parse("main.steps/Last")
    private val nestedDocument = listOf(main, stepFirst, stepLoop, loopInner, loopInnerDeep, stepLast)
    private val nestedRows = listOf(stepFirst, stepLoop, stepLast)

    // An If chain: the rows are branch objects nested under a step, and two of them own subtrees.
    private val ifStep = ObjectPath.parse("main.steps/If")
    private val branchOne = ObjectPath.parse("main.steps/If.branches/One")
    private val branchOneStep = ObjectPath.parse("main.steps/If.branches/One.steps/InOne")
    private val branchTwo = ObjectPath.parse("main.steps/If.branches/Two")
    private val branchThree = ObjectPath.parse("main.steps/If.branches/Three")
    private val branchThreeStep = ObjectPath.parse("main.steps/If.branches/Three.steps/InThree")
    private val afterIf = ObjectPath.parse("main.steps/AfterIf")
    private val branchDocument = listOf(
        main, ifStep, branchOne, branchOneStep, branchTwo, branchThree, branchThreeStep, afterIf)
    private val branchRows = listOf(branchOne, branchTwo, branchThree)


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun dropAtOwnTopEdgeIsNoOp() {
        assertNull(ObjectTreeReorder.reorderPosition(flatDocument, flatRows, source = 1, insertionIndex = 1))
    }


    @Test
    fun dropAtOwnBottomEdgeIsNoOp() {
        assertNull(ObjectTreeReorder.reorderPosition(flatDocument, flatRows, source = 1, insertionIndex = 2))
    }


    @Test
    fun singleRowCannotMove() {
        val document = listOf(main, workerA)
        val rows = listOf(workerA)
        assertNull(ObjectTreeReorder.reorderPosition(document, rows, source = 0, insertionIndex = 0))
        assertNull(ObjectTreeReorder.reorderPosition(document, rows, source = 0, insertionIndex = 1))
    }


    @Test
    fun sourceOutOfRangeIsNull() {
        assertNull(ObjectTreeReorder.reorderPosition(flatDocument, flatRows, source = 3, insertionIndex = 0))
        assertNull(ObjectTreeReorder.reorderPosition(flatDocument, flatRows, source = -1, insertionIndex = 0))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun moveDownByOne() {
        val position = ObjectTreeReorder.reorderPosition(
            flatDocument, flatRows, source = 0, insertionIndex = 2)!!

        assertEquals(PositionRelation.at(2), position)
        assertEquals(
            listOf(main, workerB, workerA, workerC),
            applyShift(flatDocument, workerA, position))
    }


    @Test
    fun moveUpByOne() {
        val position = ObjectTreeReorder.reorderPosition(
            flatDocument, flatRows, source = 2, insertionIndex = 1)!!

        assertEquals(PositionRelation.at(2), position)
        assertEquals(
            listOf(main, workerA, workerC, workerB),
            applyShift(flatDocument, workerC, position))
    }


    @Test
    fun moveToFirst() {
        val position = ObjectTreeReorder.reorderPosition(
            flatDocument, flatRows, source = 2, insertionIndex = 0)!!

        assertEquals(PositionRelation.at(1), position)
        assertEquals(
            listOf(main, workerC, workerA, workerB),
            applyShift(flatDocument, workerC, position))
    }


    @Test
    fun moveToLast() {
        val position = ObjectTreeReorder.reorderPosition(
            flatDocument, flatRows, source = 0, insertionIndex = 3)!!

        assertEquals(PositionRelation.at(3), position)
        assertEquals(
            listOf(main, workerB, workerC, workerA),
            applyShift(flatDocument, workerA, position))
    }


    @Test
    fun dropPastTheEndLandsLast() {
        val position = ObjectTreeReorder.reorderPosition(
            flatDocument, flatRows, source = 0, insertionIndex = 7)!!

        assertEquals(PositionRelation.at(3), position)
        assertEquals(
            listOf(main, workerB, workerC, workerA),
            applyShift(flatDocument, workerA, position))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun hiddenObjectsBetweenRowsOffsetTheDocumentIndex() {
        // The off-by-one this translation exists to prevent: the row index is 1, the document index is 3.
        val position = ObjectTreeReorder.reorderPosition(
            interleavedDocument, interleavedRows, source = 0, insertionIndex = 2)!!

        assertEquals(PositionRelation.at(3), position)
        assertEquals(
            listOf(main, paramTwo, unrelatedStep, paramOne, paramThree),
            applyShift(interleavedDocument, paramOne, position))
    }


    @Test
    fun hiddenObjectsMoveToFirst() {
        val position = ObjectTreeReorder.reorderPosition(
            interleavedDocument, interleavedRows, source = 2, insertionIndex = 0)!!

        assertEquals(PositionRelation.at(1), position)
        assertEquals(
            listOf(main, paramThree, paramOne, paramTwo, unrelatedStep),
            applyShift(interleavedDocument, paramThree, position))
    }


    @Test
    fun hiddenObjectsMoveToLast() {
        val position = ObjectTreeReorder.reorderPosition(
            interleavedDocument, interleavedRows, source = 0, insertionIndex = 3)!!

        assertEquals(PositionRelation.at(4), position)
        assertEquals(
            listOf(main, paramTwo, unrelatedStep, paramThree, paramOne),
            applyShift(interleavedDocument, paramOne, position))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun draggedSubtreeTravelsWithItsRoot() {
        val position = ObjectTreeReorder.reorderPosition(
            nestedDocument, nestedRows, source = 1, insertionIndex = 3)!!

        assertEquals(PositionRelation.at(3), position)
        assertEquals(
            listOf(main, stepFirst, stepLast, stepLoop, loopInner, loopInnerDeep),
            applyShift(nestedDocument, stepLoop, position))
    }


    @Test
    fun draggedSubtreeMovesUp() {
        val position = ObjectTreeReorder.reorderPosition(
            nestedDocument, nestedRows, source = 1, insertionIndex = 0)!!

        assertEquals(PositionRelation.at(1), position)
        assertEquals(
            listOf(main, stepLoop, loopInner, loopInnerDeep, stepFirst, stepLast),
            applyShift(nestedDocument, stepLoop, position))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun nestedAnchorResolvesInDocumentOrder() {
        val position = ObjectTreeReorder.reorderPosition(
            branchDocument, branchRows, source = 2, insertionIndex = 0)!!

        assertEquals(PositionRelation.at(2), position)
        assertEquals(
            listOf(main, ifStep, branchThree, branchThreeStep, branchOne, branchOneStep, branchTwo, afterIf),
            applyShift(branchDocument, branchThree, position))
    }


    @Test
    fun landingLastClearsTheLastSiblingsWholeSubtree() {
        // Anchoring on the last sibling's index alone would drop the moved branch INSIDE that sibling's subtree.
        val position = ObjectTreeReorder.reorderPosition(
            branchDocument, branchRows, source = 0, insertionIndex = 3)!!

        assertEquals(PositionRelation.at(5), position)
        assertEquals(
            listOf(main, ifStep, branchTwo, branchThree, branchThreeStep, branchOne, branchOneStep, afterIf),
            applyShift(branchDocument, branchOne, position))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun insertionPositionBeforeSibling() {
        val position = ObjectTreeReorder.insertionPosition(
            nestedDocument, stepFirst, listOf(loopInner), siblingIndex = 0, containerPath = stepLoop)

        assertEquals(PositionRelation.at(2), position)
        assertEquals(
            listOf(main, stepLoop, stepFirst, loopInner, loopInnerDeep, stepLast),
            applyShift(nestedDocument, stepFirst, position))
    }


    @Test
    fun insertionPositionAfterLastSiblingSubtree() {
        val position = ObjectTreeReorder.insertionPosition(
            nestedDocument, stepFirst, listOf(loopInner), siblingIndex = 1, containerPath = stepLoop)

        assertEquals(PositionRelation.at(4), position)
        assertEquals(
            listOf(main, stepLoop, loopInner, loopInnerDeep, stepFirst, stepLast),
            applyShift(nestedDocument, stepFirst, position))
    }


    @Test
    fun insertionPositionIntoEmptyContainerAnchorsOnTheContainer() {
        val position = ObjectTreeReorder.insertionPosition(
            nestedDocument, stepFirst, listOf(), siblingIndex = 0, containerPath = stepLoop)

        assertEquals(PositionRelation.at(4), position)
        assertEquals(
            listOf(main, stepLoop, loopInner, loopInnerDeep, stepFirst, stepLast),
            applyShift(nestedDocument, stepFirst, position))
    }


    @Test
    fun insertionPositionWithNoSiblingsAndNoContainerAppendsAtEnd() {
        val position = ObjectTreeReorder.insertionPosition(
            flatDocument, workerA, listOf(), siblingIndex = 0)

        assertEquals(PositionRelation.at(3), position)
        assertEquals(
            listOf(main, workerB, workerC, workerA),
            applyShift(flatDocument, workerA, position))
    }


    @Test
    fun anchorMissingFromTheDocumentFails() {
        assertFailsWith<IllegalStateException> {
            ObjectTreeReorder.insertionPosition(
                flatDocument, workerA, listOf(ObjectPath.parse("main.workers/Gone")), siblingIndex = 0)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** Mirrors ShiftObjectTreeCommand: lift the dragged root and its descendants out, re-insert them together. */
    private fun applyShift(
        documentPaths: List<ObjectPath>,
        draggedPath: ObjectPath,
        position: PositionRelation
    ): List<ObjectPath> {
        val subtree = documentPaths.filter { it == draggedPath || it.startsWith(draggedPath) }
        val remaining = documentPaths.filterNot { it in subtree }
        val rebuilt = remaining.toMutableList()
        rebuilt.addAll(position.resolve(remaining.size).value, subtree)
        return rebuilt
    }
}
