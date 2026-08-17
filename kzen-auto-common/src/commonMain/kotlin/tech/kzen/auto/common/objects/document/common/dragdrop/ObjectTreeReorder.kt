package tech.kzen.auto.common.objects.document.common.dragdrop

import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.PositionRelation


/**
 * Where a dragged object's subtree lands in document order.
 *
 * A drag is expressed in the indices of one visible row list — a Script branch's steps, an If's branches, a Job's
 * Workers, a signature's parameters, a Contexts document's declarations — while `ShiftObjectTreeCommand` and
 * `RelocateObjectTreeRefactorCommand` take a position in the whole document's object order. The two frames differ
 * by every object the row list doesn't show (other branches, nested subtrees, `main` itself), so this translation
 * is what keeps a drop from landing a slot away from where it was released.
 *
 * Both commands remove the dragged object's whole subtree and re-insert it contiguously, so every index here
 * resolves against the document with that subtree already gone.
 */
object ObjectTreeReorder {
    //-----------------------------------------------------------------------------------------------------------------
    /**
     * The position that moves the row at [source] to the gap [insertionIndex] — a gap index in `0..rowPaths.size`,
     * counted before the dragged row leaves its slot. Null when the drop is a no-op (the dragged row's own two
     * edges), when [source] names no row, or when the row is the only one there is.
     */
    fun reorderPosition(
        documentPaths: List<ObjectPath>,
        rowPaths: List<ObjectPath>,
        source: Int,
        insertionIndex: Int
    ): PositionRelation? {
        val draggedPath = rowPaths.getOrNull(source)
            ?: return null

        if (insertionIndex == source || insertionIndex == source + 1) {
            return null
        }

        // The dragged row leaves its slot before re-insertion, so a gap below it shifts up by one.
        val siblingIndex = if (insertionIndex > source) insertionIndex - 1 else insertionIndex

        val siblingPaths = rowPaths.filterIndexed { index, _ -> index != source }
        if (siblingPaths.isEmpty()) {
            return null
        }

        return insertionPosition(documentPaths, draggedPath, siblingPaths, siblingIndex)
    }


    /**
     * The position that places [draggedPath]'s subtree at [siblingIndex] among [siblingPaths] — the sibling
     * occupying that index is the anchor it goes immediately before. Past the last sibling it goes after that
     * sibling's whole subtree; with no siblings at all it goes after [containerPath]'s subtree, so it serializes
     * inside the container it was dropped into, or at the end of the document when no container is given.
     */
    fun insertionPosition(
        documentPaths: List<ObjectPath>,
        draggedPath: ObjectPath,
        siblingPaths: List<ObjectPath>,
        siblingIndex: Int,
        containerPath: ObjectPath? = null
    ): PositionRelation {
        val remainingPaths = documentPaths.filter {
            it != draggedPath && !it.startsWith(draggedPath)
        }

        val anchorPath = siblingPaths.getOrNull(siblingIndex)

        val documentIndex =
            when {
                anchorPath != null -> {
                    val anchorIndex = remainingPaths.indexOf(anchorPath)
                    check(anchorIndex != -1) { "Not in document: $anchorPath" }
                    anchorIndex
                }

                siblingPaths.isNotEmpty() ->
                    afterSubtree(remainingPaths, siblingPaths.last())

                containerPath != null ->
                    afterSubtree(remainingPaths, containerPath)

                else ->
                    remainingPaths.size
            }

        return PositionRelation.at(documentIndex)
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** One past the last path of [rootPath]'s subtree — where a following sibling of [rootPath] begins. */
    private fun afterSubtree(remainingPaths: List<ObjectPath>, rootPath: ObjectPath): Int {
        return remainingPaths.indexOfLast { it == rootPath || it.startsWith(rootPath) } + 1
    }
}
