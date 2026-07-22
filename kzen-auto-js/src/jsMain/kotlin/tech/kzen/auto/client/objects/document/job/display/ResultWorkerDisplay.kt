package tech.kzen.auto.client.objects.document.job.display

import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ResultWorkerDisplayProps: WorkerDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper
    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}


//---------------------------------------------------------------------------------------------------------------------
// Display for a ResultSink Worker: the default card plus a value box rendered into the card body via
// WorkerDisplayDefault.bodyExtra — the kept result value (e.g. "fizzbuzz"), so a Job's output is visible on the
// card instead of just its count. Sources everything from the always-on pushed progress (props.common.progress —
// ResultSinkWorker publishes the value's display text under JobConventions.progressResultValueKey), so unlike the
// serve-polling Summary / Preview displays this needs no rest client / store / observer: JobController threads a
// fresh value-stable `common` each poll, so live updates (keep=last shows the running latest) flow through
// RPureComponent for free. Registered via `display: ResultWorkerDisplay` in notation (job-worker.yaml), the exact
// SummaryWorkerDisplay composition (see CC-17).
@Suppress("unused")
class ResultWorkerDisplay(
    props: ResultWorkerDisplayProps
):
    RPureComponent<ResultWorkerDisplayProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        private val attributeViewManager: AttributeViewManager.Wrapper,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        WorkerDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: WorkerDisplayProps.() -> Unit) {
            ResultWorkerDisplay::class.react {
                this.attributeEditorManager = this@Wrapper.attributeEditorManager
                this.attributeViewManager = this@Wrapper.attributeViewManager
                this.clientStateGlobal = this@Wrapper.clientStateGlobal
                this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        WorkerDisplayDefault::class.react {
            this.attributeEditorManager = props.attributeEditorManager
            this.attributeViewManager = props.attributeViewManager
            this.clientStateGlobal = props.clientStateGlobal
            this.mirroredGraphStore = props.mirroredGraphStore
            this.common = props.common
            this.bodyExtra = { it.renderValue() }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The kept value published by ResultSinkWorker (a single-element list under progressResultValueKey — the
    // PreviewWorkerDisplay.parseRows wire-cast pattern). This display owns that key's schema; kept here, not in
    // the shared JobWorkerProgress, so a 3rd-party Worker's payload never touches general code. Renders nothing
    // until a value has been kept (the box appears once the run produces one; an empty stream shows none).
    private fun ChildrenBuilder.renderValue() {
        val value = (props.common.progress?.progressMap?.get(JobConventions.progressResultValueKey) as? List<*>)
            ?.firstOrNull()
            ?.toString()
            ?: return

        div {
            css {
                marginTop = 0.5.em
                fontSize = 0.8.em
            }

            div {
                css {
                    color = NamedColor.gray
                }
                +"Value"
            }

            div {
                css {
                    marginTop = 0.25.em
                    padding = Padding(0.4.em, 0.6.em, 0.4.em, 0.6.em)
                    border = Border(1.px, LineStyle.solid, NamedColor.lightgray)
                    borderRadius = 3.px
                    backgroundColor = NamedColor.whitesmoke
                    fontFamily = FontFamily.monospace
                    whiteSpace = WhiteSpace.preWrap
                    overflowWrap = OverflowWrap.anywhere
                    maxHeight = 20.em
                    overflowY = Auto.auto
                }
                +value
            }
        }
    }
}
