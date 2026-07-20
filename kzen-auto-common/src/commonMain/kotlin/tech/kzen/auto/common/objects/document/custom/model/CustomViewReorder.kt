package tech.kzen.auto.common.objects.document.custom.model

import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath


/**
 * Translation between the Custom view's object list and the underlying document object list. The view hides the
 * root `main` object, so a drag expressed in view indices is off by however many hidden entries precede it — this
 * is the arithmetic that keeps a reorder from landing one slot away from where it was dropped.
 */
object CustomViewReorder {
    //-----------------------------------------------------------------------------------------------------------------
    data class DropShift(
        val sourcePath: ObjectPath,
        val newDocPosition: Int
    )


    //-----------------------------------------------------------------------------------------------------------------
    /** View list = document object list minus the root `main` object. */
    fun viewPaths(allDocPaths: List<ObjectPath>): List<ObjectPath> {
        return allDocPaths.filterNot(::isFilteredFromView)
    }


    /**
     * Translate a reorder expressed in view indices (the source, and the post-drop view index the caller already
     * computed from target/dropAfter) into the position a ShiftObjectCommand needs, which is a document index.
     * Null when either index is out of range or the move is a no-op.
     */
    fun dropShift(allDocPaths: List<ObjectPath>, sourceViewIndex: Int, newViewIndex: Int): DropShift? {
        val viewPaths = viewPaths(allDocPaths)
        if (sourceViewIndex !in viewPaths.indices || newViewIndex !in viewPaths.indices) {
            return null
        }
        if (newViewIndex == sourceViewIndex) {
            return null
        }

        val sourcePath = viewPaths[sourceViewIndex]
        val anchorPath = anchorAfterMove(viewPaths, sourceViewIndex, newViewIndex)

        val newDocPosition =
            if (anchorPath == null) {
                allDocPaths.size - 1
            }
            else {
                val sourceDocIndex = allDocPaths.indexOf(sourcePath)
                val anchorDocIndex = allDocPaths.indexOf(anchorPath)
                if (sourceDocIndex < anchorDocIndex) anchorDocIndex - 1 else anchorDocIndex
            }

        return DropShift(sourcePath, newDocPosition)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun isFilteredFromView(objectPath: ObjectPath): Boolean {
        return objectPath.name == ObjectName.main && objectPath.nesting.isRoot()
    }


    /** The view entry that ends up immediately after the moved one, or null when it lands last. */
    private fun anchorAfterMove(viewPaths: List<ObjectPath>, source: Int, newViewIndex: Int): ObjectPath? {
        val reordered = viewPaths.toMutableList()
        val moved = reordered.removeAt(source)
        reordered.add(newViewIndex, moved)
        return reordered.getOrNull(newViewIndex + 1)
    }
}
