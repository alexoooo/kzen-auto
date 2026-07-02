package tech.kzen.auto.client.objects.document.job

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.IconButton
import mui.material.Size
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.objects.document.common.dragdrop.dragHandle
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.refCallback
import tech.kzen.auto.common.objects.document.job.JobChannelPorts
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import web.cssom.*
import web.html.HTMLDivElement


//---------------------------------------------------------------------------------------------------------------------
external interface JobObjectSlotProps: Props {
    var objectLocation: ObjectLocation
    var indexInParent: Int

    // Whether this Worker serves a live preview sample (a PreviewServer serve port) — an inline sample table +
    // "Larger sample" pull. Derived from JobServeCapability, so any preview-serving Worker qualifies, not only
    // the built-in PreviewWorker (see CC-17).
    var showPreview: Boolean

    // For an Explore worker with a non-empty PERSISTED table: a ready-to-use <a href> URL that streams the whole
    // result set as table.csv (JobController builds it from the Worker's notation location). Null otherwise (not
    // an Explore worker, or no rows). The table persists past the run, so the button stays after the run ends —
    // that's what makes the Job usable for reporting.
    var exploreDownloadLink: String?

    // This object's live progress / on-demand preview slice (Workers only; null for a Channel). Kept
    // value-stable upstream so a non-dragged slot bails out during a drag (see JobController).
    var progress: JobWorkerProgress?
    var previewDetail: JobWorkerProgress?
    var active: Boolean

    var graphStructure: GraphStructure
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper

    var isDragSource: Boolean
    var handleColor: Color

    // onDragStart takes the slot's own index so the parent holds a single stable reference for all slots
    // (the slot threads its indexInParent back in) — mirrors ScriptStepSlot. Drag-over / drop are handled at
    // the stage level (one drop zone), not per slot.
    var onDragStart: (Int) -> Unit
    var onDragEnd: () -> Unit
    var onDelete: (ObjectLocation) -> Unit
    var onQueryPreview: (ObjectLocation) -> Unit
}


//---------------------------------------------------------------------------------------------------------------------
// One Worker card in the Job stage: a white node card with live status, a Run drill-in link, attribute editors,
// and (for a Preview worker) a live sample table. The Channels connecting Workers are NOT cards — they render
// as gold pipes in the gaps between cards (JobChannelPipe), derived from Worker order. A memoized RPureComponent
// so the frequent drag-hover re-renders of JobController — which only change the drop indicator, not any card's
// props — bail out here instead of rebuilding every attribute editor and Preview table. Registers its root
// element with JobCardRowRegistry so the stage can map a drag cursor onto an insertion index by card midpoints.
class JobObjectSlot(
    props: JobObjectSlotProps
):
    RPureComponent<JobObjectSlotProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val workerBorder = Color("#c4c4c4")
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

            renderWorkerCard()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderWorkerCard() {
        div {
            css {
                padding = Padding(0.5.em, 0.75.em, 0.5.em, 0.75.em)
                border = Border(1.px, LineStyle.solid, workerBorder)
                borderRadius = 3.px
                backgroundColor = NamedColor.white
            }

            cardHeader {
                span {
                    css {
                        fontFamily = FontFamily.monospace
                        marginLeft = 0.5.em
                        color = NamedColor.gray
                    }
                    +statusText(props.progress)
                }

                renderAttributeSummaries()
            }

            renderAttributeEditors()

            if (props.showPreview) {
                renderPreview()
            }

            if (props.exploreDownloadLink != null) {
                renderExploreDownload()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Download the whole accumulated result set of an Explore worker as table.csv — a plain <a href> to the
    // notation-resolved /job/download endpoint that serves the persisted table even after the run ends (mirrors
    // Report's OutputTableController download button).
    private fun ChildrenBuilder.renderExploreDownload() {
        val downloadLink = props.exploreDownloadLink
            ?: return

        div {
            css {
                marginTop = 0.5.em
            }

            a {
                css {
                    textDecoration = None.none
                }

                href = downloadLink

                Button {
                    variant = ButtonVariant.outlined
                    size = Size.small

                    icon("material-symbols:cloud-download") {
                        style = unsafeJso {
                            marginRight = 0.25.em
                        }
                    }

                    +"Download"
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.cardHeader(
        trailing: ChildrenBuilder.() -> Unit
    ) {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                marginBottom = 0.25.em
            }

            span {
                css {
                    fontWeight = FontWeight.bold
                }
                +props.objectLocation.objectPath.name.value
            }

            trailing()

            div {
                css {
                    flexGrow = number(1.0)
                }
            }

            IconButton {
                title = "Delete"
                size = Size.small
                onClick = { props.onDelete(props.objectLocation) }
                icon("material-symbols:delete-outline") {}
            }
        }
    }


    private fun statusText(progress: JobWorkerProgress?): String {
        if (progress == null) {
            return "—"
        }

        val parts = mutableListOf<String>()
        progress.status?.let { parts.add(it) }
        if (progress.counts.isNotEmpty()) {
            parts.add(progress.counts.entries.joinToString(" ") { "${it.key}=${it.value}" })
        }
        return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
    }


    // Generic per-attribute summary views: any attribute whose metadata declares a `summary:` view is rendered
    // through the shared AttributeViewManager — no worker-type gate (see CC-17). A RunWorker's `instructions`
    // declares ReferenceLinkAttributeView, so its child-document drill-in link renders here; any future worker
    // attribute that declares a summary view gets it for free. Mirrors the Script step header's summary row
    // (ScriptStepDisplayDefault.findSummaryAttributes / StepHeader.renderSummary).
    private fun ChildrenBuilder.renderAttributeSummaries() {
        val objectMetadata = props.graphStructure.graphMetadata.objectMetadata.get(props.objectLocation)
            ?: return

        for ((attributeName, attributeMetadata) in objectMetadata.attributes.map) {
            val hasSummaryView = attributeMetadata
                .attributeMetadataNotation
                .get(AttributeViewManager.summaryAttributePath.toNesting())
                ?.asString()
                ?.isNotEmpty()
                ?: false
            if (! hasSummaryView) {
                continue
            }

            div {
                key = Key(attributeName.value)
                css {
                    marginLeft = 0.75.em
                }
                props.attributeViewManager.child(this) {
                    this.objectLocation = props.objectLocation
                    this.attributeName = attributeName
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The live sample for a Preview worker: the always-on pushed teaser ([progress]), replaced by a larger
    // on-demand slice once the user queries the Worker over its duplex `serve` channel ([previewDetail]).
    private fun ChildrenBuilder.renderPreview() {
        val detail = props.previewDetail
        val shown = detail ?: props.progress
        val active = props.active

        div {
            css {
                marginTop = 0.5.em
            }

            div {
                css {
                    fontSize = 0.8.em
                    color = NamedColor.gray
                }
                val count = shown?.rowCount
                val suffix = when {
                    detail != null -> " (live — larger sample)"
                    active -> " (live)"
                    else -> " (final)"
                }
                +("Sample" + (count?.let { " — $it row(s) total" } ?: "") + suffix)
            }

            if (shown != null && (shown.header.isNotEmpty() || shown.rows.isNotEmpty())) {
                renderPreviewTable(shown.header, shown.rows)
            }

            Button {
                variant = ButtonVariant.outlined
                size = Size.small
                disabled = ! active
                onClick = { props.onQueryPreview(props.objectLocation) }
                +"Larger sample"
            }
        }
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


    //-----------------------------------------------------------------------------------------------------------------
    // An editor for each non-managed attribute via the shared AttributeEditorManager — scalars (path,
    // delimiter, buffer, ...) fall to the default value editor; channel-reference attributes dispatch to
    // SelectChannelEditor via their `editor:` metadata. Attributes are in fixed metadata order.
    private fun ChildrenBuilder.renderAttributeEditors() {
        val objectMetadata: ObjectMetadata = props.graphStructure
            .graphMetadata
            .objectMetadata
            .get(props.objectLocation)
            ?: return

        for ((attributeName, attributeMetadata) in objectMetadata.attributes.map) {
            // Skip managed metadata attributes (icon / title / ...) and channel-endpoint ports: a port is now
            // order-managed (the gold pipes between Worker cards), not wired per-Worker via a dropdown.
            if (AutoConventions.isManaged(attributeName) ||
                    JobChannelPorts.isChannelPort(attributeMetadata.type)) {
                continue
            }

            div {
                css {
                    marginBottom = 0.25.em
                }
                renderAttributeEditor(attributeName)
            }
        }
    }


    private fun ChildrenBuilder.renderAttributeEditor(attributeName: AttributeName) {
        props.attributeEditorManager.child(this) {
            this.objectLocation = props.objectLocation
            this.attributeName = attributeName
        }
    }
}
