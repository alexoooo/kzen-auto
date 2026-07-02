package tech.kzen.auto.client.objects.document.job.display

import react.Props
import tech.kzen.auto.client.objects.document.job.JobWorkerProgress
import tech.kzen.lib.common.model.location.ObjectLocation


external interface WorkerDisplayProps: Props {
    var common: WorkerDisplayPropsCommon
}


// The generic per-Worker facts every card needs, threaded from JobController's single progress poll: the Worker's
// location, its live progress teaser (status / counts / sample — kept value-stable upstream so a non-dragged card
// bails), and whether a run is active. Everything richer a card needs it self-injects via its @Reflect Wrapper (see
// WorkerDisplayDefault) or reaches through the DocumentBridge (see SummaryWorkerDisplay) — mirroring
// ScriptStepDisplayPropsCommon, which likewise crosses the manager boundary with only the minimal payload, so the
// generic controller/slot carry no Worker-type knowledge (see CC-17).
data class WorkerDisplayPropsCommon(
    val objectLocation: ObjectLocation,
    val progress: JobWorkerProgress?,
    val active: Boolean
)
