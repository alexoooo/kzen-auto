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
import tech.kzen.auto.client.objects.document.common.dragdrop.dragHandle
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.refCallback
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import web.cssom.*
import web.html.HTMLDivElement


//---------------------------------------------------------------------------------------------------------------------
external interface JobObjectSlotProps: Props {
    var objectLocation: ObjectLocation
    var indexInParent: Int

    // True for a Channel (rendered as a gold pipe-styled bar), false for a Worker (white node card).
    var isChannel: Boolean
    var external: Boolean
    var isPreviewWorker: Boolean
    var isRunWorker: Boolean

    // This object's live progress / on-demand preview slice (Workers only; null for a Channel). Kept
    // value-stable upstream so a non-dragged slot bails out during a drag (see JobController).
    var progress: JobWorkerProgress?
    var previewDetail: JobWorkerProgress?
    var active: Boolean

    var graphStructure: GraphStructure
    var attributeEditorManager: AttributeEditorManager.Wrapper

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
// One card in the Job stage: a Worker (white node card with status / Run drill-in link / Preview sample) or a
// Channel (a gold pipe-styled bar, echoing the Flow Pipe so a connector reads distinctly from a node). A
// memoized RPureComponent so the frequent drag-hover re-renders of JobController — which only change the drop
// indicator, not any slot's props — bail out here instead of rebuilding every attribute editor and Preview
// table. Registers its root element with JobCardRowRegistry so the stage can map a drag cursor onto an
// insertion index by card midpoints.
class JobObjectSlot(
    props: JobObjectSlotProps
):
    RPureComponent<JobObjectSlotProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Pipe palette, echoing Flow's gold edge colours (EdgeController) so a Channel reads as a connector
        // rather than a node — a light gold fill, a gold border, and a darker gold for the icon / accents.
        private val channelFill = Color("#fff7d6")
        private val channelBorder = Color("#e8c200")
        private val channelAccent = Color("#9a7b00")

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
                maxWidth = if (props.isChannel) 32.em else 40.em

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

            if (props.isChannel) {
                renderChannelCard()
            }
            else {
                renderWorkerCard()
            }
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

                if (props.isRunWorker) {
                    renderRunWorkerLink()
                }
            }

            renderAttributeEditors()

            if (props.isPreviewWorker) {
                renderPreview()
            }
        }
    }


    private fun ChildrenBuilder.renderChannelCard() {
        div {
            css {
                padding = Padding(0.35.em, 0.85.em, 0.5.em, 0.85.em)
                border = Border(1.px, LineStyle.solid, channelBorder)
                // Rounded "pipe" ends; gold fill so a connector reads distinctly from the white node cards.
                borderRadius = 1.25.em
                backgroundColor = channelFill
            }

            cardHeader(
                leading = {
                    // Icon inherits the span's font-size (sizing) and currentColor (gold accent).
                    span {
                        css {
                            display = Display.inlineFlex
                            alignItems = AlignItems.center
                            marginRight = 0.4.em
                            fontSize = 1.25.em
                            color = channelAccent
                        }
                        icon("material-symbols:swap-horiz") {}
                    }
                }
            ) {
                if (props.external) {
                    span {
                        css {
                            marginLeft = 0.5.em
                            color = channelAccent
                        }
                        +"(external)"
                    }
                }
            }

            renderAttributeEditors()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.cardHeader(
        leading: (ChildrenBuilder.() -> Unit)? = null,
        trailing: ChildrenBuilder.() -> Unit
    ) {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                marginBottom = 0.25.em
            }

            leading?.invoke(this)

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


    // A Run Worker hosts another Logic (its `instructions`) once per element; surface a drill-in link to that
    // child document so its independently trace-recorded live execution can be opened. Mirrors the reference
    // resolution + hash navigation of ReferenceLinkAttributeView.
    private fun ChildrenBuilder.renderRunWorkerLink() {
        val graphNotation = props.graphStructure.graphNotation

        val reference = graphNotation
            .firstAttribute(props.objectLocation, AttributePath.ofName(AttributeName("instructions")))
            ?.asString()
            ?.takeIf { it.isNotEmpty() }
            ?.let { ObjectReference.parse(it) }
            ?: return

        val documentPath = graphNotation.coalesce
            .locateOptional(reference, ObjectReferenceHost.ofLocation(props.objectLocation))
            ?.documentPath
            ?: return

        a {
            css {
                display = Display.inlineFlex
                alignItems = AlignItems.center
                marginLeft = 0.75.em
                fontSize = 0.85.em
                color = Color("rgba(0, 0, 0, 0.55)")
                textDecoration = Globals.initial
                cursor = Cursor.pointer
                "&:hover" {
                    color = Color("#1565ff")
                }
            }

            href = NavigationRoute(documentPath, RequestParams.empty).toFragment()
            title = "Open the document this Run Worker executes"

            onClick = { it.stopPropagation() }

            span { +documentPath.name.value }

            icon("material-symbols:open-in-new") {
                style = unsafeJso {
                    fontSize = 1.em
                    marginLeft = 0.25.em
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

        for ((attributeName, _) in objectMetadata.attributes.map) {
            if (AutoConventions.isManaged(attributeName)) {
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
