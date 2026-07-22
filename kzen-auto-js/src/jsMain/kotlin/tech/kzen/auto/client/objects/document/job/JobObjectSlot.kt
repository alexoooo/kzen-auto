package tech.kzen.auto.client.objects.document.job

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.dragdrop.dragHandle
import tech.kzen.auto.client.objects.document.job.display.WorkerDisplayManager
import tech.kzen.auto.client.objects.document.job.display.WorkerDisplayPropsCommon
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.refCallback
import tech.kzen.auto.common.objects.document.logic.StepValidation
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.*
import web.html.HTMLDivElement


//---------------------------------------------------------------------------------------------------------------------
external interface JobObjectSlotProps: Props {
    var objectLocation: ObjectLocation
    var indexInParent: Int

    // This Worker's live progress teaser (status / counts / sample). Kept value-stable upstream so a non-dragged
    // slot bails out during a drag (see JobController). Threaded on to the card via WorkerDisplayPropsCommon.
    var progress: JobWorkerProgress?

    // This Worker's server-side validation slice (inferred payload type + expression error), fetched by
    // JobController on notation change. Threaded on to the card via WorkerDisplayPropsCommon.
    var validation: StepValidation?

    var active: Boolean

    // Resolves + mounts this Worker's own declared card component (`display:` marker) — the slot never knows the
    // Worker type (see CC-17).
    var workerDisplayManager: WorkerDisplayManager.Wrapper

    var isDragSource: Boolean
    var handleColor: Color

    // onDragStart takes the slot's own index so the parent holds a single stable reference for all slots
    // (the slot threads its indexInParent back in) — mirrors ScriptStepSlot. Drag-over / drop are handled at
    // the stage level (one drop zone), not per slot.
    var onDragStart: (Int) -> Unit
    var onDragEnd: () -> Unit
}


//---------------------------------------------------------------------------------------------------------------------
// One Worker slot in the Job stage: the drag affordance + a mount point for the Worker's own card, resolved from
// its `display:` marker by WorkerDisplayManager (the analog of ScriptStepSlot). The slot itself carries no
// Worker-type knowledge — the card (header / editors / preview / download / summary poll) is entirely contributed
// by the Worker archetype's declared display component (see CC-17). The Channels between Workers are NOT cards —
// they render as gold pipes in the gaps (JobChannelDisplay), derived from Worker order. A memoized RPureComponent so
// the frequent drag-hover re-renders of JobController — which only change the drop indicator, not any card's props
// — bail out here. Registers its root element with JobCardRowRegistry so the stage can map a drag cursor onto an
// insertion index by card midpoints.
class JobObjectSlot(
    props: JobObjectSlotProps
):
    RPureComponent<JobObjectSlotProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    // Kept stable across renders so WorkerDisplayManager (RPureComponent) can bail out when nothing card-relevant
    // changed (e.g. a drag-hover re-render that only moved the drop indicator). Mirrors ScriptStepSlot.
    private var cachedCommon: WorkerDisplayPropsCommon? = null


    private fun commonForProps(): WorkerDisplayPropsCommon {
        val existing = cachedCommon
        if (existing != null &&
            existing.objectLocation === props.objectLocation &&
            existing.progress == props.progress &&
            existing.validation == props.validation &&
            existing.active == props.active
        ) {
            return existing
        }
        val fresh = WorkerDisplayPropsCommon(
            props.objectLocation,
            props.progress,
            props.validation,
            props.active)
        cachedCommon = fresh
        return fresh
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            // NB: hover reveal of the drag handle is pure CSS (no hover state field) — a state toggle would
            //     re-reconcile this slot on every mouse move (a false positive in React DevTools' highlight
            //     overlay even though RPureComponent bails). data-job-slot is the selector hook.
            asDynamic()["data-job-slot"] = ""

            css {
                position = Position.relative
                maxWidth = 40.em

                "&:hover > [data-drag-handle]" {
                    opacity = number(1.0)
                }
            }

            // The root element is what JobController measures for drag-insertion; register/unregister via the
            // callback ref (React 19 invokes the returned cleanup on detach).
            ref = refCallback { element: HTMLDivElement ->
                JobCardRowRegistry.register(props.objectLocation, element)
                val cleanup: () -> Unit = { JobCardRowRegistry.unregister(props.objectLocation, element) }
                cleanup
            }

            dragHandle(
                isVisible = props.isDragSource,
                handleColor = props.handleColor,
                onStart = { props.onDragStart(props.indexInParent) },
                onEnd = props.onDragEnd,
                frosted = true)

            props.workerDisplayManager.child(this) {
                common = commonForProps()
            }
        }
    }
}
