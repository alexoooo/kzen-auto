package tech.kzen.auto.client.objects.document.script.display

import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.objects.document.script.step.header.StepNameEditor
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation


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
    objectLocation: ObjectLocation
): StepTraceInfo {
    val traceValues: Map<LogicTracePath, ExecutionValue>? = scriptState
        .progress
        .logicTraceSnapshot
        ?.values

    val trace = traceValues
        ?.get(LogicTracePath.ofObjectLocation(objectLocation))
        ?.let { StepTrace.ofExecutionValue(it) }

    val nextToRun = traceValues
        ?.get(ScriptConventions.nextStepTracePath)
        ?.get()
        ?.let { ObjectLocation.parse(it as String) }

    return StepTraceInfo(trace, nextToRun == objectLocation)
}
