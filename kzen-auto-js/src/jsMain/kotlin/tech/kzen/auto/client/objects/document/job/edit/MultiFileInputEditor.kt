package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import mui.material.IconButton
import mui.material.InputLabel
import mui.material.Size
import mui.material.TextField
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
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.platform.collect.toPersistentList
import web.cssom.Auto
import web.cssom.Cursor
import web.cssom.FontFamily
import web.cssom.NamedColor
import web.cssom.VerticalAlign
import web.cssom.em
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface MultiFileInputEditorProps: AttributeEditorProps {
    // The document-agnostic file-listing REST client, injected by the Wrapper (this editor is the exception to the
    // "editors stay pure" rule — directory browse is a static filesystem query, not a run-scoped serve query).
    var restClient: ClientRestApi
}


external interface MultiFileInputEditorState: State {
    // The Worker's committed input file paths, in READ order (the list order IS the concatenation order the
    // MultiFileReaderWorker reads files in). Value-compared on refresh so an unrelated command doesn't re-render.
    var paths: List<String>?

    // Transient browse state (ephemeral UI, not persisted): the directory being browsed, the name filter, and the
    // last listing (null = not loaded).
    var directory: String
    var filter: String
    var entries: List<DataLocationInfo>?
    var listingError: String?
    var loading: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
// Edits a MultiFileReaderWorker's `paths` attribute — an ORDERED List<String> of concrete input file paths (the
// worker reads them concatenated in list order). Wired via `editor: MultiFileInputEditor` in the archetype
// metadata; the generic DefaultAttributeEditor can't edit a list attribute, and directory browse needs a REST
// call the generic editor doesn't make.
//
// Two parts: (1) the SELECTED PATHS list — the committed `paths` as reorderable / removable rows (order matters,
// so up / down move a file earlier / later in the read sequence); (2) a BROWSER — a directory field + name
// filter that lists a directory via the document-agnostic /file-listing route (reusing the server's
// FileListingAction), letting the user click into subdirectories and add files. Directory-browse resolves the
// concrete, ordered path list the worker consumes at EDIT time (the worker never re-globs mid-run — that keeps
// its resume cursor deterministic).
//
// Writes rewrite the WHOLE `paths` list via UpsertAttributeCommand: robust whether the list is inherited-only (a
// freshly palette-inserted worker) or materialized, robust against duplicates, and makes reordering trivial —
// there is no `paths` spec class with canonical builders, so this localized list-notation write is the seam.
@Suppress("unused")
class MultiFileInputEditor(
    props: MultiFileInputEditorProps
):
    RComponent<MultiFileInputEditorProps, MultiFileInputEditorState>(props),
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val restClient: ClientRestApi
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            MultiFileInputEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                restClient = this@Wrapper.restClient
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun MultiFileInputEditorState.init(props: MultiFileInputEditorProps) {
        val graphNotation = props.clientStateGlobal.current()!!.graphStructure().graphNotation
        val initialPaths = readPaths(graphNotation)

        paths = initialPaths
        // Start the browser at the first selected file's directory (if any), so re-opening the editor lands where
        // the user was; empty otherwise (they enter a directory to begin).
        directory = initialPaths.firstOrNull()?.let { parentDirectory(it) } ?: ""
        filter = ""
        entries = null
        listingError = null
        loading = false
    }


    private fun readPaths(graphNotation: GraphNotation): List<String> {
        val attributeNotation = graphNotation
            .firstAttribute(props.objectLocation, props.attributeName) as? ListAttributeNotation
            ?: return listOf()

        return attributeNotation.values.mapNotNull { (it as? ScalarAttributeNotation)?.value }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
            props.mirroredGraphStore.observe(this)
        }

        if (state.directory.isNotEmpty()) {
            performLoad(state.directory, state.filter)
        }
    }


    override fun componentWillUnmount() {
        props.mirroredGraphStore.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        refreshPaths(graphDefinition.graphStructure.graphNotation)
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        refreshPaths(graphDefinitionAttempt.graphStructure.graphNotation)
    }


    private fun refreshPaths(graphNotation: GraphNotation) {
        if (props.objectLocation !in graphNotation.coalesce) {
            // The containing Worker was deleted; its parent card hasn't re-rendered to drop us yet.
            return
        }

        val nextPaths = readPaths(graphNotation)
        if (state.paths != nextPaths) {
            setState {
                paths = nextPaths
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Rewrite the whole ordered `paths` list. Read the current list OUTSIDE the setState lambda (that lambda runs
    // against an empty object — see wrap/React.kt); apply reads state.paths in the add / delete / move helpers.
    private fun applyPaths(newPaths: List<String>) {
        val listNotation = ListAttributeNotation(
            newPaths.map { ScalarAttributeNotation(it) as AttributeNotation }.toPersistentList())

        async {
            props.mirroredGraphStore.apply(
                UpsertAttributeCommand(props.objectLocation, props.attributeName, listNotation))
        }
    }


    private fun addPath(path: String) {
        val current = state.paths ?: listOf()
        if (path in current) {
            return
        }
        applyPaths(current + path)
    }


    private fun deletePath(index: Int) {
        val current = state.paths ?: return
        if (index !in current.indices) {
            return
        }
        applyPaths(current.filterIndexed { i, _ -> i != index })
    }


    // Move a path one slot earlier (-1) or later (+1): reorders the read sequence.
    private fun movePath(index: Int, delta: Int) {
        val current = state.paths ?: return
        val target = index + delta
        if (index !in current.indices || target !in current.indices) {
            return
        }

        val reordered = current.toMutableList()
        val moved = reordered.removeAt(index)
        reordered.add(target, moved)
        applyPaths(reordered)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun performLoad(directory: String, filter: String) {
        setState {
            loading = true
            listingError = null
        }

        async {
            try {
                val listing = props.restClient.listFiles(directory, filter)
                setState {
                    entries = listing
                    loading = false
                }
            }
            catch (e: Throwable) {
                setState {
                    listingError = e.message ?: "Failed to list directory"
                    entries = null
                    loading = false
                }
            }
        }
    }


    private fun reload() {
        performLoad(state.directory, state.filter)
    }


    private fun navigateTo(dir: String) {
        setState {
            directory = dir
        }
        performLoad(dir, state.filter)
    }


    private fun onDirectoryChange(value: String) {
        setState {
            directory = value
        }
    }


    private fun onFilterChange(value: String) {
        setState {
            filter = value
        }
    }


    // The parent of a filesystem path (handles both separators); null at a filesystem root.
    private fun parentDirectory(path: String): String? {
        val trimmed = path.trimEnd('/', '\\')
        val slashIndex = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
        return when {
            slashIndex < 0 -> null
            slashIndex == 0 -> "/"
            else -> trimmed.substring(0, slashIndex)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val paths = state.paths
            ?: return

        renderSelectedPaths(paths)
        renderBrowser(paths)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderSelectedPaths(paths: List<String>) {
        InputLabel {
            css {
                fontSize = 0.8.em
            }
            +CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
        }

        if (paths.isEmpty()) {
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
                for ((index, path) in paths.withIndex()) {
                    tr {
                        key = Key(path)

                        td {
                            IconButton {
                                title = "Move earlier"
                                disabled = index == 0
                                onClick = { movePath(index, -1) }
                                icon("material-symbols:arrow-upward") {}
                            }
                        }

                        td {
                            IconButton {
                                title = "Move later"
                                disabled = index == paths.size - 1
                                onClick = { movePath(index, 1) }
                                icon("material-symbols:arrow-downward") {}
                            }
                        }

                        td {
                            IconButton {
                                title = "Remove file"
                                onClick = { deletePath(index) }
                                icon("material-symbols:delete") {}
                            }
                        }

                        td {
                            css {
                                verticalAlign = VerticalAlign.middle
                                fontFamily = FontFamily.monospace
                            }
                            +path
                        }
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderBrowser(currentPaths: List<String>) {
        div {
            css {
                marginTop = 0.5.em
            }

            div {
                span {
                    css {
                        width = 24.em
                        display = web.cssom.Display.inlineBlock
                    }
                    TextField {
                        label = ReactNode("Directory")
                        fullWidth = true
                        size = Size.small
                        value = state.directory
                        onChange = {
                            onDirectoryChange((it.target as HTMLInputElement).value)
                        }
                        onKeyDown = { e ->
                            ClientInputUtils.handleEnter(e) { reload() }
                        }
                    }
                }

                IconButton {
                    title = "List directory"
                    onClick = { reload() }
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
                    onChange = {
                        onFilterChange((it.target as HTMLInputElement).value)
                    }
                    onKeyDown = { e ->
                        ClientInputUtils.handleEnter(e) { reload() }
                    }
                }
            }

            renderListing(currentPaths)
        }
    }


    private fun ChildrenBuilder.renderListing(currentPaths: List<String>) {
        val entries = state.entries

        when {
            state.loading ->
                div {
                    css { marginTop = 0.25.em }
                    +"Loading…"
                }

            state.listingError != null ->
                div {
                    css {
                        marginTop = 0.25.em
                        color = NamedColor.red
                    }
                    +"Error: ${state.listingError}"
                }

            entries == null ->
                div {
                    css {
                        marginTop = 0.25.em
                        color = NamedColor.gray
                    }
                    +"Enter a directory and press Enter to browse."
                }

            else ->
                div {
                    css {
                        marginTop = 0.25.em
                        maxHeight = 20.em
                        overflowY = Auto.auto
                    }

                    val parent = parentDirectory(state.directory)
                    if (parent != null) {
                        renderParentRow(parent)
                    }

                    for (entry in entries) {
                        renderEntryRow(entry, currentPaths)
                    }

                    if (entries.isEmpty() && parent == null) {
                        div {
                            css { color = NamedColor.gray }
                            +"(empty)"
                        }
                    }
                }
        }
    }


    private fun ChildrenBuilder.renderParentRow(parent: String) {
        div {
            key = Key("..")
            css {
                cursor = Cursor.pointer
            }
            onClick = { navigateTo(parent) }
            icon("material-symbols:drive-folder-upload") {}
            +" .."
        }
    }


    private fun ChildrenBuilder.renderEntryRow(entry: DataLocationInfo, currentPaths: List<String>) {
        val pathString = entry.path.asString()

        div {
            key = Key(pathString)

            if (entry.directory) {
                css {
                    cursor = Cursor.pointer
                }
                onClick = { navigateTo(pathString) }
                icon("material-symbols:folder") {}
                +" ${entry.name}"
            }
            else {
                val alreadyAdded = pathString in currentPaths

                IconButton {
                    title = if (alreadyAdded) "Already added" else "Add file"
                    disabled = alreadyAdded
                    onClick = { addPath(pathString) }
                    icon("material-symbols:add-circle-outline") {}
                }
                icon("material-symbols:description") {}
                +" ${entry.name}"
            }
        }
    }
}
