package tech.kzen.auto.client.objects.document.script.display.image

import tech.kzen.auto.client.objects.document.script.display.computeStepHeaderInfo
import tech.kzen.auto.client.objects.document.script.display.computeStepTraceInfo
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.lib.common.exec.BinaryValue
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceEvent
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


//---------------------------------------------------------------------------------------------------------------------
// One screenshot as the user sees it on the page, in reading order: either a step's right-of-step
// thumbnail (a normal step's latest frame, or a RunStep's latest subtree frame) or one frame of an
// expanded RunStep's detail film strip. `key` is a stable identity used to find this entry for
// prev/next navigation and to match the thumbnail the full-screen viewer was opened from; `title` is
// the ready-to-render header label ("<document> > <step>").
data class PageScreenshotEntry(
    val key: String,
    val title: String,
    val image: BinaryValue
) {
    companion object {
        // Distinct namespaces so a step key and a frame key can never collide.
        fun stepKey(location: ObjectLocation): String =
            "step:" + location.toReference().asString()

        fun frameKey(sequence: Long): String =
            "frame:$sequence"
    }
}


//---------------------------------------------------------------------------------------------------------------------
// The screenshots visible on the current Script page, in reading order: for each step in document order
// its right-of-step thumbnail (when it has one); for an open RunStep its detail film strip (the
// sub-script screenshots) replaces that single thumbnail, in the same execution-grouped order the strip
// renders. Mirrors what StepImageThumbnail and RunStepDisplay show, so the full-screen viewer's
// left/right walks exactly the thumbnails the user sees. Built from the page document's ScriptState (its
// trace timeline and per-step expansion) plus the graph (step titles, RunStep subtree roots).
//
// A RunStep's representative IS the latest of its strip frames, so both key by frame sequence (closed =
// just that one frame; open = the whole strip, which contains it). Keying both the same way — rather
// than a separate "step" entry for the representative — means the latest frame appears once, not as both
// a leading representative and a trailing strip frame, so navigation reads strictly chronologically.
fun pageScreenshots(
    scriptState: ScriptState,
    clientState: ClientState,
    objectStableMapper: ObjectStableMapper
): List<PageScreenshotEntry> {
    val documentPath = scriptState.mainLocation.documentPath
    val entries = mutableListOf<PageScreenshotEntry>()

    for (objectPath in scriptState.scriptTree.orderedDescendantObjectPaths()) {
        val location = documentPath.toObjectLocation(objectPath)

        // representativeFrame is non-null only for a RunStep (with at least one subtree screenshot).
        val representative = scriptState.progress.representativeFrame(objectStableMapper.objectStableId(location))
        if (representative != null) {
            val frames =
                if (scriptState.isStepExpanded(location)) {
                    stripFrames(scriptState, objectStableMapper, location)
                }
                else {
                    listOf(representative)
                }
            for (frame in frames) {
                val image = frame.value as? BinaryValue
                    ?: continue
                entries.add(PageScreenshotEntry(
                    PageScreenshotEntry.frameKey(frame.sequence),
                    frameTitle(clientState, objectStableMapper, frame),
                    image))
            }
        }
        else {
            // Any other step: its own latest frame, shown as the right-of-step thumbnail.
            val image = computeStepTraceInfo(scriptState, location, objectStableMapper)
                .trace?.detail as? BinaryValue
            if (image != null) {
                entries.add(PageScreenshotEntry(
                    PageScreenshotEntry.stepKey(location),
                    stepTitle(clientState, location),
                    image))
            }
        }
    }

    // Each execution belongs to exactly one RunStep's owned set (the execution tree is a tree), so a
    // screenshot frame appears under a single step — no cross-step duplicate keys to dedupe.
    return entries
}


//---------------------------------------------------------------------------------------------------------------------
// A RunStep's detail-strip frames in the order the strip renders them: subtree screenshot events grouped
// by sub-script execution (first-appearance order), each group in execution (sequence) order. Mirrors
// RunStepDisplay.buildGroups so navigation order matches the visible strip even when nested sub-script
// executions interleave by sequence.
private fun stripFrames(
    scriptState: ScriptState,
    objectStableMapper: ObjectStableMapper,
    runStepLocation: ObjectLocation
): List<LogicTraceEvent> {
    val ownedExecutions = scriptState.progress.ownedExecutions(
        objectStableMapper.objectStableId(runStepLocation))

    val byExecution = LinkedHashMap<String, MutableList<LogicTraceEvent>>()
    for (frame in scriptState.progress.traceEvents) {
        if (frame.value !is BinaryValue || frame.executionId.value !in ownedExecutions) {
            continue
        }
        byExecution.getOrPut(frame.executionId.value) { mutableListOf() }.add(frame)
    }
    return byExecution.values.flatten()
}


//---------------------------------------------------------------------------------------------------------------------
private fun stepTitle(clientState: ClientState, location: ObjectLocation): String {
    val title = computeStepHeaderInfo(clientState, location)?.title ?: ""
    return "${location.documentPath.name.value} > $title"
}


private fun frameTitle(
    clientState: ClientState,
    objectStableMapper: ObjectStableMapper,
    frame: LogicTraceEvent
): String {
    val location =
        try {
            objectStableMapper.objectLocation(frame.objectStableId)
        }
        catch (_: IllegalArgumentException) {
            return "?"
        }
    val title = computeStepHeaderInfo(clientState, location)?.title ?: ""
    return "${location.documentPath.name.value} > $title"
}
