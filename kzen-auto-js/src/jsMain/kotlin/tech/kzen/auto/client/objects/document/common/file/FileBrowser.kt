package tech.kzen.auto.client.objects.document.common.file

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Checkbox
import mui.material.CircularProgress
import mui.material.IconButton
import mui.material.InputAdornment
import mui.material.InputAdornmentPosition
import mui.material.Size
import mui.material.TextField
import mui.system.sx
import react.ChildrenBuilder
import react.Key
import react.Props
import react.ReactNode
import react.State
import react.create
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.dom.onChange
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.edit.DebouncedSubmitter
import tech.kzen.auto.client.objects.document.common.edit.DocumentEditActivity
import tech.kzen.auto.client.objects.document.common.edit.documentEditActivity
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.inputSlotProps
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import web.cssom.*
import web.html.HTMLDivElement
import web.html.HTMLInputElement


/** Presentation-only file browser shared by Report Input and Job file-selection adapters. */
external interface FileBrowserProps: Props {
    var directory: DataLocation
    var filter: String
    var listing: List<DataLocationInfo>?
    var loading: Boolean
    var error: String?
    var checked: Set<DataLocation>
    var selected: Set<DataLocation>
    var onDirectorySelected: (DataLocation) -> Unit
    var onFilterChanged: (String) -> Unit
    var onCheckedChanged: (Set<DataLocation>) -> Unit
    var onAdd: (List<DataLocation>) -> Unit
    var onRemove: (List<DataLocation>) -> Unit
}


external interface FileBrowserState: State {
    var editingPath: Boolean
    var pathText: String
    var filterText: String
    var rangeAnchor: Int?
}


/**
 * Owns the filter's visible draft until its submitted value is reflected back through props.
 *
 * A graph commit publishes asynchronously.  Without the dirty guard, that older publication can replace text typed
 * while the commit was in flight.  External values resume winning once they acknowledge the current draft.
 */
internal class FileBrowserFilterDraft(
    initialExternalValue: String,
    private val submit: (String) -> Unit,
    editActivity: () -> DocumentEditActivity?,
    delayMillis: Int = DebouncedSubmitter.defaultDelayMillis
) {
    private var externalValue = initialExternalValue
    private var dirty = false

    var value = initialExternalValue
        private set

    private val submitter = DebouncedSubmitter(delayMillis, editActivity) {
        submitIfChanged()
    }


    fun edit(nextValue: String) {
        if (value == nextValue) {
            return
        }
        value = nextValue
        dirty = true
        submitter.schedule()
    }


    /** Returns a replacement for React state, or null when the visible value should stay unchanged. */
    fun synchronize(nextExternalValue: String): String? {
        externalValue = nextExternalValue
        if (value == nextExternalValue) {
            dirty = false
            return null
        }
        if (dirty) {
            return null
        }
        value = nextExternalValue
        return nextExternalValue
    }


    fun flush() {
        submitter.flush()
    }


    fun submitImmediately() {
        submitter.cancel()
        submitIfChanged()
    }


    private fun submitIfChanged() {
        if (value == externalValue) {
            dirty = false
            return
        }
        submit(value)
    }
}


internal object FileBrowserSelection {
    fun toggle(
        paths: List<DataLocation>,
        checked: Set<DataLocation>,
        index: Int,
        anchor: Int?,
        shift: Boolean
    ): Set<DataLocation> {
        val next = checked.toMutableSet()
        if (shift && anchor != null && anchor in paths.indices) {
            val range = minOf(anchor, index)..maxOf(anchor, index)
            val shouldSelect = paths[anchor] in checked
            for (fileIndex in range) {
                val path = paths[fileIndex]
                if (shouldSelect) next.add(path) else next.remove(path)
            }
        }
        else {
            val path = paths[index]
            if (!next.add(path)) next.remove(path)
        }
        return next
    }
}


class FileBrowser(props: FileBrowserProps): RPureComponent<FileBrowserProps, FileBrowserState>(props) {
    init {
        installContextType(DocumentBridgeContext)
    }


    private val filterDraft = FileBrowserFilterDraft(
        props.filter,
        submit = { props.onFilterChanged(it) },
        editActivity = { documentEditActivity() })


    override fun FileBrowserState.init(props: FileBrowserProps) {
        editingPath = false
        pathText = props.directory.asString()
        filterText = props.filter
        rangeAnchor = null
    }


    override fun componentDidUpdate(prevProps: FileBrowserProps, prevState: FileBrowserState, snapshot: Any) {
        if (!state.editingPath && props.directory != prevProps.directory) {
            setState { pathText = props.directory.asString() }
        }
        if (props.filter != prevProps.filter) {
            filterDraft.synchronize(props.filter)?.let { synchronized ->
                setState { filterText = synchronized }
            }
        }
        if (props.listing != prevProps.listing) {
            setState { rangeAnchor = null }
        }
    }


    override fun componentWillUnmount() {
        filterDraft.flush()
    }


    override fun ChildrenBuilder.render() {
        renderControls()
        renderPath()
        props.error?.let { renderError(it) }
        renderListing()
    }


    private fun ChildrenBuilder.renderControls() {
        val files = visibleFiles()
        val add = files.map { it.path }.filter { it in props.checked && it !in props.selected }
        val remove = files.map { it.path }.filter { it in props.checked && it in props.selected }

        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                gap = 0.75.em
                flexWrap = FlexWrap.wrap
            }
            actionButton("Add", "material-symbols:add-circle-outline", add, props.onAdd)
            actionButton("Remove", "material-symbols:do-not-disturb-on-outline", remove, props.onRemove)
            div { css { flexGrow = number(1.0) } }

            TextField {
                size = Size.small
                sx { width = 20.em }
                // The field is not a glob: FileListingAction.parseFilter keeps names containing EVERY
                // whitespace-separated word. Said in the field's own placeholder and hover rather than on a
                // line above it — the hint is only wanted while the field is empty, which is exactly when a
                // placeholder shows.
                placeholder = "Search e.g. sales csv"
                title = "Matches names containing all of these words"
                inputSlotProps = unsafeJso {
                    startAdornment = InputAdornment.create {
                        position = InputAdornmentPosition.start
                        icon("material-symbols:search") {}
                    }
                }
                value = state.filterText
                onChange = {
                    val value = (it.target as HTMLInputElement).value
                    filterDraft.edit(value)
                    setState { filterText = value }
                }
                onBlur = { filterDraft.flush() }
                onKeyDown = { event: react.dom.events.KeyboardEvent<HTMLDivElement> ->
                    ClientInputUtils.handleEnter(event) {
                        filterDraft.submitImmediately()
                    }
                }
            }
            if (props.loading) {
                CircularProgress { sx { width = 1.5.em; height = 1.5.em } }
            }
        }
    }


    private fun ChildrenBuilder.actionButton(
        label: String,
        iconName: String,
        locations: List<DataLocation>,
        action: (List<DataLocation>) -> Unit
    ) {
        Button {
            variant = ButtonVariant.outlined
            size = Size.small
            disabled = locations.isEmpty() || props.loading
            title = if (locations.isEmpty()) "No applicable files selected" else "$label selected files"
            onClick = { action(locations) }
            icon(iconName) { style = unsafeJso { marginRight = 0.25.em } }
            +if (locations.isEmpty()) label else "$label (${locations.size})"
        }
    }

    private fun ChildrenBuilder.renderPath() {
        div {
            css { marginTop = 0.75.em; marginBottom = 0.75.em }
            if (state.editingPath) renderPathEditor() else renderBreadcrumbs()
        }
    }


    private fun ChildrenBuilder.renderPathEditor() {
        TextField {
            fullWidth = true
            size = Size.small
            sx { width = 100.pct.minus(6.em) }
            label = ReactNode("Path")
            value = state.pathText
            error = props.error != null
            onChange = { setState { pathText = (it.target as HTMLInputElement).value } }
            onKeyDown = { event -> ClientInputUtils.handleEnterAndEscape(event, ::submitPath, ::cancelPathEdit) }
        }
        IconButton {
            title = "Cancel"
            onClick = { cancelPathEdit() }
            icon("material-symbols:cancel") {}
        }
        IconButton {
            title = "Browse path"
            onClick = { submitPath() }
            icon("material-symbols:save") {}
        }
    }


    private fun submitPath() {
        val next = DataLocation.of(state.pathText)
        setState { editingPath = false }
        if (next != props.directory) props.onDirectorySelected(next)
    }


    private fun cancelPathEdit() {
        setState {
            editingPath = false
            pathText = props.directory.asString()
        }
    }


    private fun ChildrenBuilder.renderBreadcrumbs() {
        div {
            css { display = Display.flex; alignItems = AlignItems.center; flexWrap = FlexWrap.wrap }
            for ((index, part) in props.directory.ancestors().withIndex()) {
                if (index != 0) {
                    icon("material-symbols:arrow-forward-ios") {
                        style = unsafeJso {
                            fontSize = 0.75.em
                            marginLeft = 0.25.em
                            marginRight = 0.25.em
                            color = NamedColor.grey
                        }
                    }
                }
                span {
                    key = Key(part.asString())
                    title = part.asString()
                    css {
                        paddingLeft = 0.25.em
                        paddingRight = 0.25.em
                        fontSize = 1.1.em
                        cursor = Cursor.pointer
                        if (props.error != null) color = NamedColor.red
                        hover { backgroundColor = NamedColor.lightgrey }
                    }
                    onClick = { props.onDirectorySelected(part) }
                    +part.fileName()
                }
            }
            IconButton {
                size = Size.small
                title = "Edit path"
                onClick = { setState { editingPath = true; pathText = props.directory.asString() } }
                icon("material-symbols:edit") {}
            }
        }
    }


    private fun ChildrenBuilder.renderError(error: String) {
        div {
            css { color = NamedColor.red; marginBottom = 0.5.em }
            +"Error: $error"
        }
    }


    private fun ChildrenBuilder.renderListing() {
        val listing = props.listing
        if (listing == null) {
            div { css { fontFamily = FontFamily.monospace }; +props.directory.asString() }
            return
        }
        if (listing.isEmpty()) {
            div { +"Empty (select a different folder or adjust the filter above)" }
            return
        }

        val folders = listing.filter { it.directory }
        val files = listing.filterNot { it.directory }
        div {
            css { fileTableScrollFrame(60.vh) }
            table {
                css { fileTableGrid() }
                renderHeader(files)
                tbody {
                    css { if (props.loading) opacity = number(0.5) }
                    renderFolders(folders)
                    renderFiles(files)
                }
            }
        }
    }


    private fun ChildrenBuilder.renderHeader(files: List<DataLocationInfo>) {
        val paths = files.map { it.path }
        val allChecked = paths.isNotEmpty() && paths.all { it in props.checked }
        val anyChecked = paths.any { it in props.checked }
        thead {
            tr {
                fileTableHeaderCell {
                    Checkbox {
                        disableRipple = true
                        disabled = paths.isEmpty()
                        checked = allChecked
                        indeterminate = anyChecked && !allChecked
                        asDynamic().inputProps = unsafeJso<Any> {
                            asDynamic()["aria-label"] = if (allChecked) "Clear file selection" else "Select all files"
                        }
                        onChange = { _, _ ->
                            props.onCheckedChanged(if (allChecked) emptySet() else paths.toSet())
                        }
                    }
                }
                fileTableHeaderCell { +"Name" }
                fileTableHeaderCell { +"Selected" }
                fileTableHeaderCell { +"Modified" }
                fileTableHeaderCell { +"Size" }
            }
        }
    }


    private fun ChildrenBuilder.renderFolders(folders: List<DataLocationInfo>) {
        for (folder in folders) {
            tr {
                key = Key(folder.path.asString())
                css { cursor = Cursor.pointer; hover { backgroundColor = NamedColor.lightgrey } }
                onClick = { props.onDirectorySelected(folder.path) }
                td { icon("material-symbols:folder-open") {} }
                td { +folder.name }
                td {}
                td { +FormatUtils.formatLocalDateTime(folder.modified) }
                td {}
            }
        }
    }


    private fun ChildrenBuilder.renderFiles(files: List<DataLocationInfo>) {
        for ((index, file) in files.withIndex()) {
            val checked = file.path in props.checked
            val selected = file.path in props.selected
            tr {
                key = Key(file.path.asString())
                css {
                    cursor = Cursor.pointer
                    if (checked) backgroundColor = FileTableColors.checkedRow
                    hover {
                        backgroundColor =
                            if (checked) FileTableColors.checkedHoverRow
                            else FileTableColors.hoverRow
                    }
                }
                onClick = { event ->
                    val dynamicEvent: dynamic = event
                    toggleChecked(files, index, dynamicEvent.shiftKey as Boolean)
                }
                td {
                    Checkbox {
                        this.checked = checked
                        asDynamic().inputProps = unsafeJso<Any> {
                            asDynamic()["aria-label"] = "Select ${file.name}"
                        }
                        onClick = { it.stopPropagation() }
                        onChange = { _, _ -> toggleChecked(files, index, false) }
                    }
                }
                td { css { if (selected) fontWeight = FontWeight.bold }; +file.name }
                td { if (selected) icon("material-symbols:check") {} }
                td { css { whiteSpace = WhiteSpace.nowrap }; +FormatUtils.formatLocalDateTime(file.modified) }
                td {
                    css { textAlign = TextAlign.right; whiteSpace = WhiteSpace.nowrap; paddingRight = 0.5.em }
                    +FormatUtils.readableFileSize(file.size)
                }
            }
        }
    }


    private fun toggleChecked(files: List<DataLocationInfo>, index: Int, shift: Boolean) {
        val next = FileBrowserSelection.toggle(
            files.map { it.path }, props.checked, index, state.rangeAnchor, shift)
        setState { rangeAnchor = index }
        props.onCheckedChanged(next)
    }


    private fun visibleFiles(): List<DataLocationInfo> = props.listing.orEmpty().filterNot { it.directory }
}
