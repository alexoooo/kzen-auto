package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import mui.material.IconButton
import mui.material.InputLabel
import mui.material.Size
import mui.material.TextField
import mui.system.sx
import react.ChildrenBuilder
import react.Key
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import react.dom.onChange
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.AttributeCommitter
import tech.kzen.auto.client.objects.document.common.edit.AttributeDraftStore
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.common.edit.documentEditActivity
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.file.FileSelectionSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.platform.collect.toPersistentList
import web.cssom.*
import web.html.HTMLInputElement


external interface FileSelectionEditorProps : AttributeEditorProps {
    var restClient: ClientRestApi
}


external interface FileSelectionEditorState : State {
    var selected: List<FileSelectionEntry>?
    var directory: String
    var filter: String
    var listing: List<DataLocationInfo>?
    var listingError: String?
    var editError: String?
    var loading: Boolean
}


class FileSelectionEditor(
    props: FileSelectionEditorProps
) :
    RComponent<FileSelectionEditorProps, FileSelectionEditorState>(props),
    LocalGraphStore.Observer
{
    companion object {
        private val directoryAttributeName = AttributeName("directory")
        private val filterAttributeName = AttributeName("filter")
        private val legacyPathsAttributeName = AttributeName("paths")
    }


    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val restClient: ClientRestApi
    ) : AttributeEditor(objectLocation) {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            FileSelectionEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                restClient = this@Wrapper.restClient
                block()
            }
        }
    }


    init {
        installContextType(DocumentBridgeContext)
    }


    private var mounted = false
    private var pendingSelection: List<FileSelectionEntry>? = null
    private var listingEpoch = 0

    private val committer = AttributeCommitter(
        graphStore = { this.props.mirroredGraphStore },
        objectLocation = { this.props.objectLocation },
        attributePath = { AttributePath.ofName(this.props.attributeName) },
        pendingNotation = { pendingSelection?.let(::selectionNotation) },
        onCommitted = { committed ->
            if (pendingSelection?.let(::selectionNotation) == committed) {
                pendingSelection = null
            }
        },
        onError = { message -> setState { editError = message } },
        editActivity = { documentEditActivity() })


    override fun FileSelectionEditorState.init(props: FileSelectionEditorProps) {
        val graphNotation = props.clientStateGlobal.current()!!.graphStructure().graphNotation
        val selected = readSelection(graphNotation)
        this.selected = selected
        directory = readString(graphNotation, directoryAttributeName)
            ?: selected.firstOrNull()?.location?.parent()?.asString().orEmpty()
        filter = readString(graphNotation, filterAttributeName).orEmpty()
        listing = null
        listingError = null
        editError = null
        loading = false
    }


    override fun componentDidMount() {
        mounted = true
        async {
            if (mounted) {
                props.mirroredGraphStore.observe(this)
            }
        }
        if (state.directory.isNotEmpty()) {
            load(state.directory, state.filter)
        }
    }


    override fun componentWillUnmount() {
        mounted = false
        listingEpoch++
        committer.flush()
        props.mirroredGraphStore.unobserve(this)
    }


    override suspend fun onCommandSuccess(
        event: NotationEvent,
        graphDefinition: GraphDefinitionAttempt,
        attachment: LocalGraphStore.Attachment
    ) {
        refresh(graphDefinition.graphStructure.graphNotation)
    }


    override suspend fun onCommandFailure(
        command: NotationCommand,
        cause: Throwable,
        attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        refresh(graphDefinitionAttempt.graphStructure.graphNotation)
    }


    private fun refresh(graphNotation: GraphNotation) {
        if (props.objectLocation !in graphNotation.coalesce) {
            return
        }

        if (pendingSelection == null) {
            val selected = readSelection(graphNotation)
            if (state.selected != selected) {
                setState { this.selected = selected }
            }
        }

        val directory = readString(graphNotation, directoryAttributeName)
            ?: state.directory
        val filter = readString(graphNotation, filterAttributeName)
            ?: state.filter
        if (state.directory != directory || state.filter != filter) {
            setState {
                this.directory = directory
                this.filter = filter
            }
        }
    }


    private fun readSelection(graphNotation: GraphNotation): List<FileSelectionEntry> {
        val list = graphNotation.firstAttribute(props.objectLocation, props.attributeName)
            as? ListAttributeNotation
            ?: return emptyList()
        if (list.values.all { it is MapAttributeNotation }) {
            return FileSelectionSpec.ofNotation(list).entries
        }
        return list.values.mapNotNull { value ->
            (value as? ScalarAttributeNotation)?.value?.let {
                FileSelectionEntry(DataLocation.of(it), null, null)
            }
        }
    }


    private fun readString(graphNotation: GraphNotation, attributeName: AttributeName): String? {
        return (graphNotation.firstAttribute(
            props.objectLocation, AttributePath.ofName(attributeName))
            as? ScalarAttributeNotation)?.value
    }


    private fun selectionNotation(entries: List<FileSelectionEntry>): ListAttributeNotation {
        if (props.attributeName == legacyPathsAttributeName) {
            return ListAttributeNotation(entries.map {
                ScalarAttributeNotation(it.location.asString()) as AttributeNotation
            }.toPersistentList())
        }
        return FileSelectionSpec(entries).asNotation()
    }


    private fun changeSelection(entries: List<FileSelectionEntry>, debounce: Boolean) {
        pendingSelection = entries
        setState { selected = entries }
        if (debounce) {
            committer.schedule()
        }
        else {
            committer.cancel()
            async { committer.commitNow(selectionNotation(entries)) }
        }
    }


    private fun add(location: String) {
        val current = state.selected ?: emptyList()
        if (current.any { it.location.asString() == location }) {
            return
        }
        changeSelection(current + FileSelectionEntry(DataLocation.of(location), null, null), false)
    }


    private fun remove(index: Int) {
        val current = state.selected ?: return
        if (index !in current.indices) {
            return
        }
        changeSelection(current.filterIndexed { entryIndex, _ -> entryIndex != index }, false)
    }


    private fun move(index: Int, delta: Int) {
        val current = state.selected ?: return
        val target = index + delta
        if (index !in current.indices || target !in current.indices) {
            return
        }
        val reordered = current.toMutableList()
        val moved = reordered.removeAt(index)
        reordered.add(target, moved)
        changeSelection(reordered, false)
    }


    private fun editFormat(index: Int, value: String) {
        val current = state.selected ?: return
        if (index !in current.indices) {
            return
        }
        val edited = current.toMutableList()
        edited[index] = edited[index].copy(
            format = value.takeIf { it.isNotBlank() }?.let(CommonPluginCoordinate::ofString))
        changeSelection(edited, true)
    }


    private fun editEncoding(index: Int, value: String) {
        val current = state.selected ?: return
        if (index !in current.indices) {
            return
        }
        val edited = current.toMutableList()
        edited[index] = edited[index].copy(
            encoding = value.takeIf { it.isNotBlank() }?.let(CommonDataEncodingSpec::ofString))
        changeSelection(edited, true)
    }


    private fun load(directory: String, filter: String) {
        val epoch = ++listingEpoch
        setState {
            loading = true
            listingError = null
        }
        async {
            try {
                val listing = props.restClient.listFiles(directory, filter)
                if (mounted && listingEpoch == epoch) {
                    setState {
                        this.listing = listing
                        loading = false
                    }
                }
            }
            catch (cause: Throwable) {
                if (mounted && listingEpoch == epoch) {
                    setState {
                        listingError = cause.message ?: "Failed to list directory"
                        listing = null
                        loading = false
                    }
                }
            }
        }
    }


    private fun navigateTo(directory: String) {
        if (props.attributeName == legacyPathsAttributeName) {
            setState { this.directory = directory }
            load(directory, state.filter)
            return
        }
        setState { this.directory = directory }
        async {
            props.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                directoryAttributeName,
                ScalarAttributeNotation(directory)))
            load(directory, state.filter)
        }
    }


    private fun browse() {
        val (directory, filter) = browserValues()
        if (directory.isEmpty()) {
            return
        }
        setState {
            this.directory = directory
            this.filter = filter
        }
        load(directory, filter)
    }


    private fun browserValues(): Pair<String, String> {
        if (props.attributeName == legacyPathsAttributeName) {
            return state.directory to state.filter
        }
        val drafts = contextValue<DocumentBridge?>()?.channel(AttributeDraftStore.Key)
        val directory = drafts
            ?.value(props.objectLocation, AttributePath.ofName(directoryAttributeName))
            ?: state.directory
        val filter = drafts
            ?.value(props.objectLocation, AttributePath.ofName(filterAttributeName))
            ?: state.filter
        return directory to filter
    }


    override fun ChildrenBuilder.render() {
        val selected = state.selected ?: return
        renderSelected(selected)
        renderBrowser(selected)
        state.editError?.let { error ->
            div {
                css { color = Color("#c62828") }
                +error
            }
        }
    }


    private fun ChildrenBuilder.renderSelected(selected: List<FileSelectionEntry>) {
        InputLabel {
            sx { fontSize = 0.8.em }
            +CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
        }

        if (selected.isEmpty()) {
            div {
                css {
                    color = NamedColor.gray
                    marginBottom = 0.25.em
                }
                +"No files selected — browse below to add."
            }
            return
        }

        table {
            tbody {
                for ((index, entry) in selected.withIndex()) {
                    tr {
                        key = Key(entry.location.asString())
                        td {
                            IconButton {
                                title = "Move earlier"
                                disabled = index == 0
                                onClick = { move(index, -1) }
                                icon("material-symbols:arrow-upward") {}
                            }
                        }
                        td {
                            IconButton {
                                title = "Move later"
                                disabled = index == selected.lastIndex
                                onClick = { move(index, 1) }
                                icon("material-symbols:arrow-downward") {}
                            }
                        }
                        td {
                            IconButton {
                                title = "Remove file"
                                onClick = { remove(index) }
                                icon("material-symbols:delete") {}
                            }
                        }
                        td {
                            css {
                                verticalAlign = VerticalAlign.middle
                                fontFamily = FontFamily.monospace
                            }
                            +entry.location.asString()
                        }
                        if (props.attributeName != legacyPathsAttributeName) {
                            td { entryTextField("Format", entry.format?.asString().orEmpty()) {
                                editFormat(index, it)
                            } }
                            td { entryTextField("Encoding", entry.encoding?.asString().orEmpty()) {
                                editEncoding(index, it)
                            } }
                        }
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.entryTextField(
        label: String,
        value: String,
        onValue: (String) -> Unit
    ) {
        TextField {
            this.label = ReactNode(label)
            this.value = value
            size = Size.small
            sx { width = 9.em }
            onChange = { onValue((it.target as HTMLInputElement).value) }
            onBlur = { committer.flush() }
        }
    }


    private fun ChildrenBuilder.renderBrowser(selected: List<FileSelectionEntry>) {
        div {
            css { marginTop = 0.5.em }

            if (props.attributeName == legacyPathsAttributeName) {
                renderLegacyBrowserControls()
            }
            else {
                div {
                    css { color = Color("rgba(0, 0, 0, 0.6)") }
                    +if (state.directory.isEmpty()) {
                        "Enter a directory above, then browse."
                    }
                    else {
                        "Directory: ${state.directory}"
                    }
                }
                renderFilterHelp()

                IconButton {
                    title = "Browse directory"
                    onClick = { browse() }
                    icon("material-symbols:refresh") {}
                }
            }

            renderListing(selected)
        }
    }


    private fun ChildrenBuilder.renderLegacyBrowserControls() {
        div {
            span {
                css {
                    width = 24.em
                    display = Display.inlineBlock
                }
                TextField {
                    label = ReactNode("Directory")
                    fullWidth = true
                    size = Size.small
                    value = state.directory
                    onChange = { stateValue ->
                        setState { directory = (stateValue.target as HTMLInputElement).value }
                    }
                    onKeyDown = { event -> ClientInputUtils.handleEnter(event) { browse() } }
                }
            }

            IconButton {
                title = "Browse directory"
                onClick = { browse() }
                icon("material-symbols:refresh") {}
            }
        }

        div {
            css {
                width = 24.em
                marginTop = 0.25.em
            }
            TextField {
                label = ReactNode("Filter")
                fullWidth = true
                size = Size.small
                value = state.filter
                onChange = { stateValue ->
                    setState { filter = (stateValue.target as HTMLInputElement).value }
                }
                onKeyDown = { event -> ClientInputUtils.handleEnter(event) { browse() } }
            }
        }
        renderFilterHelp()
    }


    private fun ChildrenBuilder.renderFilterHelp() {
        div {
            css {
                color = Color("rgba(0, 0, 0, 0.55)")
                fontSize = 0.8.em
            }
            +"Filter matches names containing all of these words, e.g. sales csv."
        }
    }


    private fun ChildrenBuilder.renderListing(selected: List<FileSelectionEntry>) {
        when {
            state.loading -> div { +"Loading…" }
            state.listingError != null -> div {
                css { color = Color("#c62828") }
                +"Error: ${state.listingError}"
            }
            state.listing == null -> div {
                css { color = NamedColor.gray }
                +"Browse to list files."
            }
            else -> div {
                css {
                    maxHeight = 20.em
                    overflowY = Auto.auto
                }
                parentDirectory(state.directory)?.let { parent ->
                    div {
                        key = Key("..")
                        css { cursor = Cursor.pointer }
                        onClick = { navigateTo(parent) }
                        icon("material-symbols:drive-folder-upload") {}
                        +" .."
                    }
                }
                for (entry in state.listing.orEmpty()) {
                    listingRow(entry, selected)
                }
            }
        }
    }


    private fun ChildrenBuilder.listingRow(
        entry: DataLocationInfo,
        selected: List<FileSelectionEntry>
    ) {
        val location = entry.path.asString()
        div {
            key = Key(location)
            if (entry.directory) {
                css { cursor = Cursor.pointer }
                onClick = { navigateTo(location) }
                icon("material-symbols:folder") {}
                +" ${entry.name}"
            }
            else {
                val alreadySelected = selected.any { it.location.asString() == location }
                IconButton {
                    title = if (alreadySelected) "Already selected" else "Add file"
                    disabled = alreadySelected
                    onClick = { add(location) }
                    icon("material-symbols:add-circle-outline") {}
                }
                icon("material-symbols:description") {}
                +" ${entry.name}"
            }
        }
    }


    private fun parentDirectory(path: String): String? {
        val trimmed = path.trimEnd('/', '\\')
        val separator = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
        return when {
            separator < 0 -> null
            separator == 0 -> "/"
            else -> trimmed.substring(0, separator)
        }
    }
}
