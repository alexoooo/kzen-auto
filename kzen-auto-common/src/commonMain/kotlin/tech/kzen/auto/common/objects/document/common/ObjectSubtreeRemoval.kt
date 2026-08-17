package tech.kzen.auto.common.objects.document.common

import tech.kzen.lib.common.model.obj.ObjectPath


/**
 * Cascade-deleting an object means deleting everything nested under it — a step's loop body, an If branch's
 * steps — and `RemoveObjectCommand` takes one object at a time, so the order the commands are issued in is
 * what makes the delete legal: an object must be a leaf by the time its turn comes.
 */
object ObjectSubtreeRemoval {
    /**
     * [rootPath] and every object nested under it, deepest first. Same-depth objects keep their document order
     * (the sort is stable), so a subtree is removed back-to-front along one branch at a time rather than in an
     * order that depends on how the caller enumerated the document.
     */
    fun deepestFirst(documentPaths: Collection<ObjectPath>, rootPath: ObjectPath): List<ObjectPath> {
        return documentPaths
            .filter { it == rootPath || it.startsWith(rootPath) }
            .sortedByDescending { it.nesting.segments.size }
    }
}
