package tech.kzen.auto.client.objects.document.job.display

import emotion.react.css
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.objects.document.job.JobServeChannelResolver
import tech.kzen.auto.client.objects.document.job.JobWorkerProgress
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface PreviewWorkerDisplayProps: WorkerDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper
    var clientStateGlobal: ClientStateGlobal
    var restClient: ClientRestApi
    var mirroredGraphStore: MirroredGraphStore
}


external interface PreviewWorkerDisplayState: State {
    // The on-demand larger sample pulled from this Worker over its duplex `serve` channel — null until the user
    // opens it ("Larger sample"), then kept live each poll while the run is active and dropped when the run ends.
    // Distinct from the always-on pushed teaser in common.progress.
    var previewDetail: JobWorkerProgress?
}


//---------------------------------------------------------------------------------------------------------------------
// Display for a preview-serving Worker (PreviewWorker / PivotWorker): the default card plus an inline live sample
// table and a "Larger sample" button, rendered into the card body via WorkerDisplayDefault.bodyExtra — the exact
// RunStepDisplay-wraps-ScriptStepDisplayDefault composition. Self-sources everything: the teaser sample from
// common.progress, the larger slice pulled over the Worker's duplex serve channel (self-injected restClient +
// clientStateGlobal), with the slice poll owned here rather than in the generic controller (see CC-17).
@Suppress("unused")
class PreviewWorkerDisplay(
    props: PreviewWorkerDisplayProps
):
    RPureComponent<PreviewWorkerDisplayProps, PreviewWorkerDisplayState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        private val attributeViewManager: AttributeViewManager.Wrapper,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val restClient: ClientRestApi,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        WorkerDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: WorkerDisplayProps.() -> Unit) {
            PreviewWorkerDisplay::class.react {
                this.attributeEditorManager = this@Wrapper.attributeEditorManager
                this.attributeViewManager = this@Wrapper.attributeViewManager
                this.clientStateGlobal = this@Wrapper.clientStateGlobal
                this.restClient = this@Wrapper.restClient
                this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Refetch the opened slice only when the run status advances (mirrors JobController's fetch-key gate); a var,
    // not state, so bumping it never re-renders.
    private var lastSliceFetchKey: String? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun PreviewWorkerDisplayState.init(props: PreviewWorkerDisplayProps) {
        previewDetail = null
    }


    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
    }


    // Keep any opened larger slice live while the run is active (each pull is a fresh sample of the Worker's rolling
    // window), and drop it once the run ends so the persisted teaser shows instead.
    override fun onClientState(clientState: ClientState) {
        if (! clientState.clientLogicState.isActive()) {
            lastSliceFetchKey = null
            if (state.previewDetail != null) {
                setState {
                    previewDetail = null
                }
            }
            return
        }

        if (state.previewDetail == null) {
            return
        }

        // Pull a fresh slice exactly when the run advanced — see ClientLogicState.traceVersion.
        val fetchKey = clientState.clientLogicState.traceVersion()
        if (fetchKey == lastSliceFetchKey) {
            return
        }
        lastSliceFetchKey = fetchKey

        async {
            refreshSlice()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onQueryPreview() {
        async {
            refreshSlice()
        }
    }


    // Pull this Worker's current slice over its duplex `serve` channel and store it, value-equality gated so an
    // unchanged slice doesn't re-render.
    private suspend fun refreshSlice() {
        val clientState = props.clientStateGlobal.current()
            ?: return
        val parsed = querySlice(clientState)
            ?: return
        if (parsed == state.previewDetail) {
            return
        }
        setState {
            previewDetail = parsed
        }
    }


    // Issue an `offset` / `limit` slice query over the (external) duplex `serve` channel via the running logic's
    // request subscriber — the browser -> Worker request/reply path. The reply has the same shape as the teaser.
    private suspend fun querySlice(clientState: ClientState): JobWorkerProgress? {
        val logicRunInfo = clientState.clientLogicState.logicStatus?.active
            ?: return null

        val channelName = JobServeChannelResolver.serveChannelName(
            clientState.graphStructure(), props.common.objectLocation)

        val result = props.restClient.logicRequest(
            logicRunInfo.id,
            logicRunInfo.frame.executionId,
            JobConventions.channelParameter to channelName,
            JobConventions.previewOffsetParameter to "0",
            JobConventions.previewLimitParameter to "200")

        return when (result) {
            is ExecutionSuccess ->
                JobWorkerProgress.ofProgressMap(null, result.value.get())

            is ExecutionFailure ->
                null
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
            this.bodyExtra = { bodyBuilder -> bodyBuilder.renderPreview() }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The live sample: the always-on pushed teaser (common.progress), replaced by a larger on-demand slice once the
    // user queries the Worker over its duplex `serve` channel (previewDetail).
    private fun ChildrenBuilder.renderPreview() {
        val detail = state.previewDetail
        val shown = detail ?: props.common.progress
        val active = props.common.active

        val header = shown?.let { parseHeader(it.progressMap) } ?: listOf()
        val rows = shown?.let { parseRows(it.progressMap) } ?: listOf()

        div {
            css {
                marginTop = 0.5.em
            }

            div {
                css {
                    fontSize = 0.8.em
                    color = NamedColor.gray
                }
                val count = shown?.longValue(JobConventions.progressCountKey)
                val suffix = when {
                    detail != null -> " (live — larger sample)"
                    active -> " (live)"
                    else -> " (final)"
                }
                +("Sample" + (count?.let { " — $it row(s) total" } ?: "") + suffix)
            }

            if (header.isNotEmpty() || rows.isNotEmpty()) {
                renderPreviewTable(header, rows)
            }

            Button {
                variant = ButtonVariant.outlined
                size = Size.small
                disabled = ! active
                onClick = { onQueryPreview() }
                +"Larger sample"
            }
        }
    }


    // This display owns the schema of the progress its Worker publishes: the sampled header / rows keys
    // (the always-on teaser via common.progress, and the on-demand slice reply — same shape), shared with the
    // server side via JobConventions. Kept here, not in the shared JobWorkerProgress, so a 3rd-party Worker's
    // payload never touches general code.
    private fun parseHeader(map: Map<String, Any?>): List<String> {
        return (map[JobConventions.progressHeaderKey] as? List<*>)?.map { it.toString() } ?: listOf()
    }


    private fun parseRows(map: Map<String, Any?>): List<List<String>> {
        return (map[JobConventions.progressRowsKey] as? List<*>)?.map { row ->
            (row as? List<*>)?.map { it.toString() } ?: listOf()
        } ?: listOf()
    }


    private fun ChildrenBuilder.renderPreviewTable(header: List<String>, rows: List<List<String>>) {
        div {
            css {
                maxHeight = 20.em
                overflowY = Auto.auto
                marginTop = 0.25.em
                marginBottom = 0.25.em
                border = Border(1.px, LineStyle.solid, NamedColor.lightgray)
            }

            table {
                css {
                    borderCollapse = BorderCollapse.collapse
                    fontSize = 0.75.em
                    fontFamily = FontFamily.monospace
                }

                if (header.isNotEmpty()) {
                    thead {
                        tr {
                            for (headerCell in header.withIndex()) {
                                th {
                                    key = Key(headerCell.index.toString())
                                    css {
                                        padding = Padding(0.1.em, 0.4.em, 0.1.em, 0.4.em)
                                        border = Border(1.px, LineStyle.solid, NamedColor.gainsboro)
                                        textAlign = TextAlign.left
                                        position = Position.sticky
                                        top = 0.px
                                        backgroundColor = NamedColor.whitesmoke
                                    }
                                    +headerCell.value
                                }
                            }
                        }
                    }
                }

                tbody {
                    for (row in rows.withIndex()) {
                        tr {
                            key = Key(row.index.toString())
                            for (cell in row.value.withIndex()) {
                                td {
                                    key = Key(cell.index.toString())
                                    css {
                                        padding = Padding(0.1.em, 0.4.em, 0.1.em, 0.4.em)
                                        border = Border(1.px, LineStyle.solid, NamedColor.gainsboro)
                                    }
                                    +abbreviate(cell.value)
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    private fun abbreviate(value: String): String {
        return if (value.length > 50) {
            value.substring(0, 50) + "…"
        }
        else {
            value
        }
    }
}
