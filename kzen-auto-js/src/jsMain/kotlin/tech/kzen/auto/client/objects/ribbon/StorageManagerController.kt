package tech.kzen.auto.client.objects.ribbon

import emotion.react.css
import mui.material.Button
import mui.material.ButtonColor
import mui.material.ButtonVariant
import mui.material.CircularProgress
import mui.material.Dialog
import mui.material.DialogActions
import mui.material.DialogContent
import mui.material.DialogContentText
import mui.material.DialogTitle
import mui.material.IconButton
import mui.system.Breakpoint
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.util.storage.StorageAreaInfo
import tech.kzen.auto.common.util.storage.StorageBundleInfo
import web.cssom.*
import kotlin.time.Instant


//---------------------------------------------------------------------------------------------------------------------
external interface StorageManagerControllerProps: Props {
    var restClient: ClientRestApi
}


external interface StorageManagerControllerState: State {
    var open: Boolean
    var loading: Boolean
    var areas: List<StorageAreaInfo>?
    var bundlesByArea: Map<String, List<StorageBundleInfo>>
    var expandedAreaId: String?

    // Pending delete confirmation; a null bundle key with a non-null area means "delete all in area".
    var confirmingAreaId: String?
    var confirmingBundleKey: String?

    var errorMessage: String?
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Header icon + modal dialog for inspecting and reclaiming the server's on-disk storage areas
 * (compiled formulas, report runs, indexes, job outputs), in the style of a browser's site-storage manager.
 */
class StorageManagerController(
    props: StorageManagerControllerProps
):
    RPureComponent<StorageManagerControllerProps, StorageManagerControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun StorageManagerControllerState.init(props: StorageManagerControllerProps) {
        open = false
        loading = false
        areas = null
        bundlesByArea = mapOf()
        expandedAreaId = null
        confirmingAreaId = null
        confirmingBundleKey = null
        errorMessage = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onOpen() {
        setState {
            open = true
            errorMessage = null
        }
        refresh()
    }


    private fun onClose() {
        setState {
            open = false
            confirmingAreaId = null
            confirmingBundleKey = null
        }
    }


    private fun refresh() {
        val expanded = state.expandedAreaId
        setState {
            loading = true
        }
        async {
            try {
                val summary = props.restClient.storageSummary()
                val expandedBundles =
                    if (expanded == null) {
                        mapOf()
                    }
                    else {
                        mapOf(expanded to props.restClient.storageBundleList(expanded))
                    }
                setState {
                    areas = summary
                    bundlesByArea = expandedBundles
                    loading = false
                }
            }
            catch (e: Throwable) {
                setState {
                    loading = false
                    errorMessage = "Unable to load storage details - ${detailOf(e)}"
                }
            }
        }
    }


    private fun onToggleExpand(areaId: String) {
        val collapse = state.expandedAreaId == areaId
        if (collapse) {
            setState {
                expandedAreaId = null
            }
            return
        }

        setState {
            expandedAreaId = areaId
        }

        async {
            try {
                val bundles = props.restClient.storageBundleList(areaId)
                setState {
                    // Merged against the state as of the response, not a pre-request capture, so two
                    // overlapping expands don't drop each other's rows.
                    bundlesByArea = state.bundlesByArea + (areaId to bundles)
                }
            }
            catch (e: Throwable) {
                val areaName = state.areas?.firstOrNull { it.id == areaId }?.displayName ?: areaId
                val stillExpanded = state.expandedAreaId == areaId
                setState {
                    // Collapse the row rather than leaving it on a spinner that can never resolve.
                    if (stillExpanded) {
                        expandedAreaId = null
                    }
                    errorMessage = "Unable to list $areaName items - ${detailOf(e)}"
                }
            }
        }
    }


    // Throwable.message can be null; toString at least names the failure type.
    private fun detailOf(cause: Throwable): String {
        return cause.message ?: cause.toString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onDeleteRequest(areaId: String, bundleKey: String?) {
        setState {
            confirmingAreaId = areaId
            confirmingBundleKey = bundleKey
        }
    }


    private fun onDeleteCancel() {
        setState {
            confirmingAreaId = null
            confirmingBundleKey = null
        }
    }


    private fun onDeleteConfirm() {
        val areaId = state.confirmingAreaId
            ?: return
        val bundleKey = state.confirmingBundleKey

        setState {
            confirmingAreaId = null
            confirmingBundleKey = null
            loading = true
        }

        async {
            val error =
                try {
                    if (bundleKey != null) {
                        props.restClient.storageBundleDelete(areaId, bundleKey).ifEmpty { null }
                    }
                    else {
                        deleteAll(areaId)
                    }
                }
                catch (e: Throwable) {
                    "Unable to delete - ${detailOf(e)}"
                }

            setState {
                errorMessage = error
            }
            refresh()
        }
    }


    private suspend fun deleteAll(areaId: String): String? {
        val bundles = props.restClient.storageBundleList(areaId)

        val errors = mutableListOf<String>()
        for (bundle in bundles) {
            if (bundle.active) {
                errors.add("In use: ${bundle.displayName}")
                continue
            }

            val error = props.restClient.storageBundleDelete(areaId, bundle.key)
            if (error.isNotEmpty()) {
                errors.add(error)
            }
        }
        return errors.joinToString("; ").ifEmpty { null }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        span {
            IconButton {
                title = "Storage management"
                onClick = { onOpen() }
                sx {
                    color = NamedColor.black
                }

                icon("material-symbols:storage") {}
            }
        }

        renderDialog()
    }


    private fun ChildrenBuilder.renderDialog() {
        Dialog {
            open = state.open
            onClose = { _, _ -> onClose() }
            fullWidth = true
            maxWidth = Breakpoint.md

            DialogTitle {
                +"Storage"

                span {
                    css {
                        float = Float.right
                        fontSize = 0.75.em
                        color = NamedColor.gray
                    }

                    if (state.loading) {
                        CircularProgress {
                            sx {
                                width = 1.em
                                height = 1.em
                                marginRight = 0.5.em
                            }
                        }
                    }

                    val areas = state.areas
                    if (areas != null) {
                        +"Total: ${FormatUtils.readableFileSize(areas.sumOf { it.sizeBytes })}"
                    }
                }
            }

            DialogContent {
                renderErrorBanner()
                renderAreaTable()
            }

            DialogActions {
                Button {
                    variant = ButtonVariant.outlined
                    onClick = { refresh() }

                    icon("material-symbols:refresh") {}
                    +"Refresh"
                }

                Button {
                    variant = ButtonVariant.outlined
                    onClick = { onClose() }

                    +"Close"
                }
            }
        }

        renderDeleteConfirmDialog()
    }


    // Stable (possibly empty) container, so toggling the error doesn't shift sibling indexes and
    // remount the table.
    private fun ChildrenBuilder.renderErrorBanner() {
        div {
            val errorMessage = state.errorMessage
            if (errorMessage != null) {
                css {
                    color = NamedColor.crimson
                    marginBottom = 0.5.em
                }
                +"Error: $errorMessage"
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderAreaTable() {
        val areas = state.areas
            ?: return

        table {
            css {
                width = 100.pct
                borderCollapse = BorderCollapse.collapse
            }

            thead {
                tr {
                    headerCell { +"Area" }
                    headerCell { +"Items" }
                    headerCell { +"Size" }
                    headerCell {}
                }
            }

            tbody {
                for (area in areas) {
                    renderAreaRow(area)

                    if (state.expandedAreaId == area.id) {
                        renderBundleRows(area)
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.headerCell(block: ChildrenBuilder.() -> Unit) {
        th {
            css {
                textAlign = TextAlign.left
                borderBottom = Border(1.px, LineStyle.solid, NamedColor.lightgray)
                padding = Padding(0.25.em, 0.5.em)
            }
            block()
        }
    }


    private fun ChildrenBuilder.renderAreaRow(area: StorageAreaInfo) {
        tr {
            css {
                cursor = Cursor.pointer
                hover {
                    backgroundColor = Color("#f5f5f5")
                }
            }

            title = area.description
            onClick = { onToggleExpand(area.id) }

            td {
                css {
                    padding = Padding(0.25.em, 0.5.em)
                    fontWeight = FontWeight.bold
                }
                +area.displayName
            }

            td {
                css {
                    padding = Padding(0.25.em, 0.5.em)
                }
                +area.bundleCount.toString()
            }

            td {
                css {
                    padding = Padding(0.25.em, 0.5.em)
                    whiteSpace = WhiteSpace.nowrap
                }
                +FormatUtils.readableFileSize(area.sizeBytes)

                val budget = area.budgetBytes
                if (budget != null) {
                    span {
                        css {
                            color = NamedColor.gray
                        }
                        +" of ${FormatUtils.readableFileSize(budget)}"
                    }
                }
            }

            td {
                css {
                    textAlign = TextAlign.right
                }
                icon(
                    if (state.expandedAreaId == area.id) {
                        "material-symbols:expand-less"
                    }
                    else {
                        "material-symbols:expand-more"
                    }
                ) {}
            }
        }
    }


    private fun ChildrenBuilder.renderBundleRows(area: StorageAreaInfo) {
        tr {
            td {
                colSpan = 4

                css {
                    padding = Padding(0.25.em, 0.5.em, 0.75.em, 1.5.em)
                    backgroundColor = Color("#fafafa")
                }

                val bundles = state.bundlesByArea[area.id]
                if (bundles == null) {
                    CircularProgress {}
                }
                else if (bundles.isEmpty()) {
                    span {
                        css {
                            color = NamedColor.gray
                        }
                        +"Empty"
                    }
                }
                else {
                    renderBundleTable(area, bundles)
                }
            }
        }
    }


    private fun ChildrenBuilder.renderBundleTable(area: StorageAreaInfo, bundles: List<StorageBundleInfo>) {
        table {
            css {
                width = 100.pct
                borderCollapse = BorderCollapse.collapse
            }

            tbody {
                for (bundle in bundles) {
                    renderBundleRow(area, bundle)
                }
            }
        }

        if (area.deletable) {
            Button {
                variant = ButtonVariant.outlined
                color = ButtonColor.error
                size = mui.material.Size.small
                onClick = { onDeleteRequest(area.id, null) }
                sx {
                    marginTop = 0.5.em
                }

                icon("material-symbols:delete") {}
                +"Delete all"
            }
        }
    }


    private fun ChildrenBuilder.renderBundleRow(area: StorageAreaInfo, bundle: StorageBundleInfo) {
        tr {
            td {
                css {
                    padding = Padding(0.1.em, 0.5.em)
                    fontFamily = FontFamily.monospace
                    overflowWrap = OverflowWrap.anywhere
                }
                title = bundle.key
                +bundle.displayName

                if (bundle.active) {
                    span {
                        css {
                            color = NamedColor.gray
                            fontFamily = FontFamily.sansSerif
                        }
                        +" (in use)"
                    }
                }
            }

            td {
                css {
                    padding = Padding(0.1.em, 0.5.em)
                    whiteSpace = WhiteSpace.nowrap
                }
                title = "Last used"
                +FormatUtils.formatLocalDateTime(Instant.fromEpochMilliseconds(bundle.lastModifiedMillis))
            }

            td {
                css {
                    padding = Padding(0.1.em, 0.5.em)
                    textAlign = TextAlign.right
                    whiteSpace = WhiteSpace.nowrap
                }
                +FormatUtils.readableFileSize(bundle.sizeBytes)
            }

            td {
                css {
                    textAlign = TextAlign.right
                    width = 2.5.em
                }

                if (area.deletable) {
                    IconButton {
                        title =
                            if (bundle.active) {
                                "In use"
                            }
                            else {
                                "Delete"
                            }
                        disabled = bundle.active
                        size = mui.material.Size.small
                        onClick = { onDeleteRequest(area.id, bundle.key) }

                        icon("material-symbols:delete") {}
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderDeleteConfirmDialog() {
        val areaId = state.confirmingAreaId
        val bundleKey = state.confirmingBundleKey

        val area = state.areas?.singleOrNull { it.id == areaId }
        val bundle = state.bundlesByArea[areaId]?.singleOrNull { it.key == bundleKey }

        Dialog {
            open = areaId != null
            onClose = { _, _ -> onDeleteCancel() }

            DialogTitle {
                +(if (bundleKey == null) "Delete all?" else "Delete?")
            }

            DialogContent {
                DialogContentText {
                    if (area != null) {
                        if (bundleKey == null) {
                            +("This deletes all ${area.displayName} items " +
                                "(${FormatUtils.readableFileSize(area.sizeBytes)}), except any in use.")
                        }
                        else if (bundle != null) {
                            +("This deletes \"${bundle.displayName}\" " +
                                "(${FormatUtils.readableFileSize(bundle.sizeBytes)}).")
                        }
                    }
                }
            }

            DialogActions {
                Button {
                    variant = ButtonVariant.outlined
                    onClick = { onDeleteCancel() }

                    +"Cancel"
                }

                Button {
                    variant = ButtonVariant.contained
                    color = ButtonColor.error
                    onClick = { onDeleteConfirm() }

                    icon("material-symbols:delete") {}
                    +"Delete"
                }
            }
        }
    }
}
