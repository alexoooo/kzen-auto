package tech.kzen.auto.client.objects.document.script.display

import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.objects.document.script.step.header.StepNameEditor
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.RunStepInstructions
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.store.normal.ObjectStableId
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
    val traceValues: Map<LogicTracePath, ExecutionValue>? = scriptState
        .progress
        .logicTraceSnapshot
        ?.values

    // The snapshot is keyed by ObjectStableId; translate the current location through the
    // client mapper so the trace survives a rename without re-fetching from the server.
    val stableId = objectStableMapper.objectStableId(objectLocation)

    val trace = traceValues
        ?.get(LogicTracePath.ofObjectStableId(stableId))
        ?.let { StepTrace.ofExecutionValue(it) }

    val nextStableId = traceValues
        ?.get(ScriptConventions.nextStepTracePath)
        ?.get()
        ?.let { ObjectStableId(it as String) }

    return StepTraceInfo(trace, nextStableId == stableId)
}


// The location whose screenshot represents a step in the per-step thumbnail. For a RunStep that's the
// LAST screenshot-bearing step of its instructions sub-script (so the collapsed RunStep previews the
// most recent sub-script frame, updating live as it runs); for any other step it's the step itself.
// Falls back to the step location when nothing better is found, so non-RunStep behaviour is unaffected.
fun representativeScreenshotLocation(
    scriptState: ScriptState,
    stepLocation: ObjectLocation,
    graphNotation: GraphNotation,
    graphDefinition: GraphDefinition,
    objectStableMapper: ObjectStableMapper
): ObjectLocation {
    // Mid-rename the step location can be momentarily stale (the thumbnail's props update only after
    // the store publishes), and inheritanceChain throws "Missing:" on an unknown location. Bail to the
    // step itself — the stableId-keyed computeStepTraceInfo the caller runs next survives the rename.
    if (stepLocation !in graphNotation.coalesce) {
        return stepLocation
    }

    if (!RunStepInstructions.isRunStep(graphNotation, stepLocation)) {
        return stepLocation
    }

    val instructionsLocation = RunStepInstructions.instructionsLocation(graphNotation, stepLocation)
        ?: return stepLocation

    val subStepLocations = RunStepInstructions.subScriptStepLocations(graphDefinition, instructionsLocation)
    for (candidate in subStepLocations.asReversed()) {
        val trace = computeStepTraceInfo(scriptState, candidate, objectStableMapper).trace
        if (trace?.detail is BinaryExecutionValue) {
            return candidate
        }
    }

    return stepLocation
}
