package tech.kzen.auto.client.service.logic

import tech.kzen.lib.common.exec.logic.run.model.LogicRunFrameInfo
import tech.kzen.lib.common.model.document.DocumentPath


// Pure helpers over the run's frame tree (LogicStatus.active.frame). Placed beside ClientLogicState so
// both the run controls (HeaderRunController) and ProjectController can derive UI state from a live run
// without depending on each other. Keyed only off DocumentPath — never a notation lookup — so they are
// safe against stale ObjectLocations during a rename (see docs/js-architecture.md §2).
object LogicRunFrames {
    // The innermost currently-executing frame: the deepest leaf of the tree. On a frame with multiple
    // dependencies the deepest wins (ties → first encountered). For a flat single-document run this is
    // just the root frame.
    fun deepestLeaf(frame: LogicRunFrameInfo): LogicRunFrameInfo {
        var best = frame
        var bestDepth = -1

        fun visit(node: LogicRunFrameInfo, depth: Int) {
            if (node.dependencies.isEmpty()) {
                if (depth > bestDepth) {
                    bestDepth = depth
                    best = node
                }
            }
            else {
                for (dependency in node.dependencies) {
                    visit(dependency, depth + 1)
                }
            }
        }

        visit(frame, 0)
        return best
    }


    // The active frame currently displaying a given document: the DEEPEST frame whose document matches.
    // Each frame is one live invocation with its own executionId; a re-entrant document appears more than
    // once (a RunStep loop, a Job re-running the same child), and the deepest match is the innermost
    // currently-executing one — the invocation the user is stepping into. null when the document isn't live
    // in the run, so the caller falls back to the most-recent (post-run) invocation. Used to fetch a
    // document's trace by THAT invocation's execution id, so sibling / sequential invocations don't merge.
    fun frameForDocument(frame: LogicRunFrameInfo?, documentPath: DocumentPath): LogicRunFrameInfo? {
        if (frame == null) {
            return null
        }

        var best: LogicRunFrameInfo? = null
        var bestDepth = -1

        fun visit(node: LogicRunFrameInfo, depth: Int) {
            if (node.objectLocation.documentPath == documentPath && depth > bestDepth) {
                bestDepth = depth
                best = node
            }
            for (dependency in node.dependencies) {
                visit(dependency, depth + 1)
            }
        }

        visit(frame, 0)
        return best
    }


    // Flattens the tree to documentPath → stack depth (root = 0). If a document re-enters at multiple
    // depths the shallowest is kept, so the sidebar indicator is stable. Empty when no run is active.
    fun depthByDocument(frame: LogicRunFrameInfo?): Map<DocumentPath, Int> {
        if (frame == null) {
            return emptyMap()
        }

        val result = LinkedHashMap<DocumentPath, Int>()

        fun visit(node: LogicRunFrameInfo, depth: Int) {
            val documentPath = node.objectLocation.documentPath
            val existing = result[documentPath]
            if (existing == null || depth < existing) {
                result[documentPath] = depth
            }
            for (dependency in node.dependencies) {
                visit(dependency, depth + 1)
            }
        }

        visit(frame, 0)
        return result
    }
}
