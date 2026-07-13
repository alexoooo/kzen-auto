package tech.kzen.auto.client.objects.document.script.display

import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.objects.document.script.step.header.StepNameEditor
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceEntry
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


data class StepHeaderInfo(
    val icon: String,
    val description: String,
    val title: String
)


data class StepTraceInfo(
    val trace: StepTrace?,
    val isNextToRun: Boolean
)


// Returns null when the step has been deleted but the parent component hasn't re-rendered yet.
fun computeStepHeaderInfo(
    clientState: ClientState,
    objectLocation: ObjectLocation
): StepHeaderInfo? {
    val graphStructure = clientState.graphStructure()
    if (graphStructure.graphMetadata.objectMetadata[objectLocation] == null) {
        return null
    }
    return StepHeaderInfo(
        icon = StepHeader.icon(graphStructure, objectLocation),
        description = StepHeader.description(graphStructure, objectLocation),
        title = StepNameEditor.title(graphStructure, objectLocation))
}


fun computeStepTraceInfo(
    scriptState: ScriptState,
    objectLocation: ObjectLocation,
    objectStableMapper: ObjectStableMapper
): StepTraceInfo {
    val traceValues: Map<LogicTracePath, LogicTraceEntry>? = scriptState
        .progress
        .logicTraceSnapshot
        ?.values

    // The snapshot is keyed by ObjectStableId; translate the current location through the
    // client mapper so the trace survives a rename without re-fetching from the server.
    val stableId = objectStableMapper.objectStableId(objectLocation)

    val trace = traceValues
        ?.get(LogicTracePath.ofObjectStableId(stableId))
        ?.value
        ?.let { StepTrace.ofExecutionValue(it) }

    // "Next to run" is the live frame's engine-owned position (checkpoint at:), riding the status poll.
    return StepTraceInfo(trace, scriptState.progress.nextToRun == objectLocation)
}
