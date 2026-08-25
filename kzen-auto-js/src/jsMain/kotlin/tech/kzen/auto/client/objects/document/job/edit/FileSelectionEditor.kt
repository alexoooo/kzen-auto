package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.AttributeCommitter
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.common.edit.documentEditActivity
import tech.kzen.auto.client.objects.document.common.file.FileBrowser
import tech.kzen.auto.client.objects.document.common.file.FileBrowserToggleChannel
import tech.kzen.auto.client.objects.document.common.file.FileBrowserToggleKey
import tech.kzen.auto.client.objects.document.common.file.FileSelectionTable
import tech.kzen.auto.client.objects.document.common.file.fileBrowserToggle
import tech.kzen.auto.client.objects.document.job.source.DataFormatStore
import tech.kzen.auto.client.objects.document.job.source.DataFormatStoreKey
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.data.file.FileSelectionBrowserConventions
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.file.FileSelectionSpec
import tech.kzen.auto.common.data.format.FileFormatCatalog
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
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.platform.collect.toPersistentList
import web.cssom.*


external interface FileSelectionEditorProps : AttributeEditorProps {
    var restClient: ClientRestApi
}


external interface FileSelectionEditorState : State {
    var selected: List<FileSelectionEntry>?
    var directory: String
    var filter: String

    // Where [directory] actually resolved to, as the server reports it with each listing; null while none has
    // arrived for the current request. The stored value may be relative — `./` is the notation default — which
    // says nothing about where the browser is and cannot be navigated up out of, so what is shown is this.
    var browseDirectory: String?

    var listing: List<DataLocationInfo>?
    var listingError: String?
    var editError: String?
    var loading: Boolean
    var checked: Set<DataLocation>
    var selectedChecked: Set<DataLocation>
    var showDetails: Boolean
    var browserOpen: Boolean

    // Options for the per-file Format / Encoding selects under Details; null until the document's shared
    // catalogue arrives (see DataFormatStore).
    var formatCatalog: FileFormatCatalog?
}


/**
 * Ordered file selection edited entirely in place: the shared [FileBrowser], then the files it has chosen.
 *
 * The interaction is Report's ([tech.kzen.auto.client.objects.document.report.input.ReportInputController]) — an
 * empty selection pins the browser open, a non-empty one puts it behind a Browser toggle, and the selection reads
 * as a [FileSelectionTable] below it in the styling the browser itself uses. Browsing sits above the selection
 * because it is what feeds it, so the eye travels the way the work does. A card that hid its chooser behind a
 * launcher button and a modal was strictly harder to use, so there is no dialog here.
 *
 * The one thing Report's selection does not have is order, so remove and reorder act on the rows checked in that
 * table, in the same check-then-act shape the browser's own Add/Remove buttons use.
 *
 * The toggle itself may be drawn by the card's header instead of here, in which case openness is shared through
 * [FileBrowserToggleChannel] — see [toggleChannel].
 */
class FileSelectionEditor(
    props: FileSelectionEditorProps
) :
    RComponent<FileSelectionEditorProps, FileSelectionEditorState>(props),
    LocalGraphStore.Observer,
    FileBrowserToggleChannel.Observer,
    DataFormatStore.Observer
{
    companion object {
        private val legacyPathsAttributeName = AttributeName("paths")
        private val toggleSelected = Color("#e0e0e0")
        private val separatorColor = Color("#c3c3c3")
        private val errorColor = Color("#c62828")


        /**
         * Report's rule: with nothing selected the browser is the only thing on the card worth acting on, so it is
         * pinned open; any selection makes it collapsible so a configured card stays short.
         */
        internal fun browserOpen(selectionEmpty: Boolean, toggledOpen: Boolean): Boolean =
            selectionEmpty || toggledOpen


        internal fun shouldLoadListing(browserOpen: Boolean, directory: String): Boolean =
            browserOpen && directory.isNotEmpty()


        /**
         * Shifts every checked entry one position, keeping the checked entries in their relative order.
         *
         * Entries already against the edge they are moving towards stay put and hold back the ones behind them, so
         * a run of checked rows travels as a block instead of scrambling itself against the boundary.
         */
        internal fun moveChecked(
            entries: List<FileSelectionEntry>,
            checked: Set<DataLocation>,
            delta: Int
        ): List<FileSelectionEntry> {
            if (checked.isEmpty() || entries.isEmpty() || delta == 0) {
                return entries
            }

            val reordered = entries.toMutableList()
            if (delta < 0) {
                var barrier = 0
                for (index in reordered.indices) {
                    if (reordered[index].location !in checked) {
                        continue
                    }
                    if (index == barrier) {
                        barrier++
                        continue
                    }
                    reordered.add(index - 1, reordered.removeAt(index))
                }
            }
            else {
                var barrier = reordered.lastIndex
                for (index in reordered.indices.reversed()) {
                    if (reordered[index].location !in checked) {
                        continue
                    }
                    if (index == barrier) {
                        barrier--
                        continue
                    }
                    reordered.add(index + 1, reordered.removeAt(index))
                }
            }
            return reordered
        }


        internal suspend fun commitBrowserValue(
            apply: suspend () -> String?,
            onError: (String?) -> Unit,
            onCommitted: () -> Unit
        ) {
            val error = apply()
            onError(error)
            if (error == null) {
                onCommitted()
            }
        }
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
    private var requestedListing: Pair<String, String>? = null

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
        val paths = browserPaths()
        val selected = readSelection(graphNotation)
        this.selected = selected
        directory = paths
            ?.let { readString(graphNotation, it.directory) }
            ?.takeIf { it.isNotEmpty() }
            ?: selected.firstOrNull()?.location?.parent()?.asString()?.takeIf { it.isNotEmpty() }
            ?: FileSelectionBrowserConventions.defaultDirectory
        filter = paths?.let { readString(graphNotation, it.filter) }.orEmpty()
        browseDirectory = null
        listing = null
        listingError = null
        editError = null
        loading = false
        checked = emptySet()
        selectedChecked = emptySet()
        showDetails = false
        browserOpen = false
        formatCatalog = null
    }


    override fun componentDidMount() {
        mounted = true
        async {
            if (mounted) {
                props.mirroredGraphStore.observe(this)
            }
        }
        documentToggleChannel()?.observe(props.objectLocation, this)
        dataFormatStore()?.observe(this)
        ensureListingLoaded()
    }


    override fun componentDidUpdate(
        prevProps: FileSelectionEditorProps,
        prevState: FileSelectionEditorState,
        snapshot: Any
    ) {
        // The browser can become visible without anyone having asked for it — removing the last file pins it open
        // again — so the listing is fetched from wherever it ends up shown, not from each control that shows it.
        ensureListingLoaded()
    }


    override fun componentWillUnmount() {
        mounted = false
        listingEpoch++
        committer.flush()
        props.mirroredGraphStore.unobserve(this)
        // Unobserved through the document's channel rather than the hosting one: React unmounts a parent before
        // its children, so by now the header may already have released its claim.
        documentToggleChannel()?.unobserve(props.objectLocation, this)
        dataFormatStore()?.unobserve(this)
    }


    private fun documentToggleChannel(): FileBrowserToggleChannel? {
        return contextValue<DocumentBridge?>()?.channel(FileBrowserToggleKey)
    }


    // Absent outside a Job stage (the legacy `paths` attribute on a plain object, say): the selects then offer
    // Default plus whatever is already configured, which is the same graceful floor a failed fetch leaves.
    private fun dataFormatStore(): DataFormatStore? {
        return contextValue<DocumentBridge?>()?.lookup(DataFormatStoreKey)
    }


    override fun onDataFormatState(state: DataFormatStore.State) {
        val catalog = state.catalog
        if (this.state.formatCatalog == catalog) {
            return
        }
        setState { formatCatalog = catalog }
    }


    /**
     * Where this card's browser is shown and hidden from, when that is not here.
     *
     * A card header can claim the toggle (see
     * [tech.kzen.auto.client.objects.document.common.file.FileBrowserToggleChannel]); until one does — a plain
     * `FileDataSource` object, the legacy `paths` attribute, anything outside a Worker card — this editor draws its
     * own and keeps openness in its own state.
     */
    private fun toggleChannel(): FileBrowserToggleChannel? {
        return documentToggleChannel()?.takeIf { it.hosted(props.objectLocation) }
    }


    override fun onFileBrowserToggled(objectLocation: ObjectLocation) {
        val opening = toggleChannel()?.isOpen(objectLocation) ?: false
        setState { browserOpen = opening }
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
                val remainingChecked = retainChecked(selected)
                setState {
                    this.selected = selected
                    selectedChecked = remainingChecked
                }
            }
        }

        val paths = browserPaths()
            ?: return

        val directory = readString(graphNotation, paths.directory)
            ?: state.directory
        val filter = readString(graphNotation, paths.filter)
            ?: state.filter
        if (state.directory != directory || state.filter != filter) {
            setState {
                if (this.directory != directory) {
                    browseDirectory = null
                }
                this.directory = directory
                this.filter = filter
            }
        }
    }


    /** Checked rows that survive a selection change: a check on a removed file must not linger as a phantom. */
    private fun retainChecked(entries: List<FileSelectionEntry>): Set<DataLocation> {
        val remaining = entries.map { it.location }.toSet()
        return state.selectedChecked.filter { it in remaining }.toSet()
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


    private fun readString(graphNotation: GraphNotation, attributePath: AttributePath): String? {
        return (graphNotation.firstAttribute(
            props.objectLocation, attributePath)
            as? ScalarAttributeNotation)?.value
    }


    private data class BrowserPaths(
        val directory: AttributePath,
        val filter: AttributePath
    )


    /**
     * Where chooser navigation persists, or null when this attribute keeps it view-only.
     *
     * A `browser: <attribute path>` marker in the attribute's metadata opts into a navigation pair held apart from
     * a source's runtime directory query, so browsing can never turn the last visited folder into a directory scan.
     * Without the marker — the legacy `MultiFileReaderWorker.paths` case, and any third-party attribute that has
     * not opted in — navigation lives in component state and only the selection is written.
     */
    private fun browserPaths(): BrowserPaths? {
        val metadata = props.clientStateGlobal.current()
            ?.graphStructure()
            ?.graphMetadata
            ?.objectMetadata
            ?.get(props.objectLocation)
            ?.attributes
            ?.get(props.attributeName)
            ?.attributeMetadataNotation
            ?: return null
        val directory = FileSelectionBrowserConventions.directoryAttributePath(metadata)
            ?: return null
        val filter = FileSelectionBrowserConventions.filterAttributePath(metadata)
            ?: return null
        return BrowserPaths(directory, filter)
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
        // An open browser stays open across the transition out of force-open: adding the first file must not yank
        // the browser out from under the click that added it. Computed before the state write, and only ever
        // latches openness on — closing stays the toggle's job.
        val keepOpen = isBrowserOpen()
        val remainingChecked = retainChecked(entries)

        pendingSelection = entries
        setState {
            selected = entries
            selectedChecked = remainingChecked
        }
        if (keepOpen) {
            setBrowserOpen(true)
        }
        if (debounce) {
            committer.schedule()
        }
        else {
            committer.cancel()
            async { committer.commitNow(selectionNotation(entries)) }
        }
    }


    private fun moveCheckedBy(delta: Int) {
        val current = state.selected ?: return
        val reordered = moveChecked(current, state.selectedChecked, delta)
        if (reordered != current) {
            changeSelection(reordered, false)
        }
    }


    private fun editFormat(index: Int, value: String) {
        val current = state.selected ?: return
        if (index !in current.indices) {
            return
        }
        val edited = current.toMutableList()
        edited[index] = edited[index].copy(
            format = value.takeIf { it.isNotBlank() }?.let(CommonPluginCoordinate::ofString))
        // Committed at once, not debounced: picking from a list is a finished decision, unlike the keystrokes
        // these two used to be.
        changeSelection(edited, false)
    }


    private fun editEncoding(index: Int, value: String) {
        val current = state.selected ?: return
        if (index !in current.indices) {
            return
        }
        val edited = current.toMutableList()
        edited[index] = edited[index].copy(
            encoding = value.takeIf { it.isNotBlank() }?.let(CommonDataEncodingSpec::ofString))
        changeSelection(edited, false)
    }


    private fun addAll(locations: List<DataLocation>) {
        val current = state.selected ?: emptyList()
        val existing = current.map { it.location }.toSet()
        val added = locations.filter { it !in existing }
            .map { FileSelectionEntry(it, null, null) }
        if (added.isNotEmpty()) changeSelection(current + added, false)
    }


    private fun removeAll(locations: List<DataLocation>) {
        val removed = locations.toSet()
        val current = state.selected ?: return
        val next = current.filterNot { it.location in removed }
        if (next != current) changeSelection(next, false)
    }


    private fun load(directory: String, filter: String) {
        requestedListing = directory to filter
        val epoch = ++listingEpoch
        setState {
            loading = true
            listingError = null
        }
        async {
            try {
                val listing = props.restClient.listFiles(directory, filter)
                if (mounted && listingEpoch == epoch) {
                    val available = listing.files.filterNot { it.directory }.map { it.path }.toSet()
                    val checked = state.checked.filter { it in available }.toSet()
                    setState {
                        this.listing = listing.files
                        this.browseDirectory = listing.directory.asString()
                        this.checked = checked
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
        setState {
            this.directory = directory
            // Dropped with the directory it described, so the field never names the place just left. A filter
            // change deliberately keeps it — the directory has not moved.
            browseDirectory = null
        }

        val paths = browserPaths()
        if (paths == null) {
            load(directory, state.filter)
            return
        }

        async {
            // The listing itself is already in flight from the state change above; this only reports a write
            // failure, and re-asserts the fetch in case the write was what changed the directory in state.
            applyBrowserValue(paths.directory, directory) {
                ensureListingLoaded()
            }
        }
    }


    private fun updateFilter(filter: String) {
        setState { this.filter = filter }

        val paths = browserPaths()
        if (paths == null) {
            load(state.directory, filter)
            return
        }

        async {
            applyBrowserValue(paths.filter, filter) {
                ensureListingLoaded()
            }
        }
    }


    private suspend fun applyBrowserValue(
        attributePath: AttributePath,
        value: String,
        onCommitted: () -> Unit
    ) {
        commitBrowserValue(
            apply = {
                CommonEditUtils.applyCommand(
                    props.mirroredGraphStore,
                    CommonEditUtils.editCommand(
                        props.objectLocation,
                        attributePath,
                        ScalarAttributeNotation(value)))
            },
            onError = { message -> setState { editError = message } },
            onCommitted = onCommitted)
    }


    private fun isBrowserForceOpen(): Boolean {
        return state.selected.isNullOrEmpty()
    }


    private fun isBrowserOpen(): Boolean {
        return browserOpen(isBrowserForceOpen(), state.browserOpen)
    }


    /**
     * Openness travels through the channel when a header owns the toggle, so both ends of the card agree; the
     * write echoes back through [onFileBrowserToggled] rather than being applied twice.
     */
    private fun setBrowserOpen(value: Boolean) {
        val channel = toggleChannel()
        if (channel != null) {
            channel.setOpen(props.objectLocation, value)
            return
        }
        setState { browserOpen = value }
    }


    private fun toggleBrowser() {
        setBrowserOpen(! state.browserOpen)
    }


    // Listings are fetched the first time the browser is actually shown: a card that already has its files chosen
    // costs no directory read until someone opens it. Keyed on what was asked for rather than on what has arrived,
    // so a failed or in-flight request is not re-issued on every render.
    private fun ensureListingLoaded() {
        val directory = state.directory
        val filter = state.filter
        if (! shouldLoadListing(isBrowserOpen(), directory) || requestedListing == directory to filter) {
            return
        }
        load(directory, filter)
    }


    override fun ChildrenBuilder.render() {
        val selected = state.selected ?: return

        // No attribute caption: this editor fills a card whose title already names what it holds — a Worker of
        // type File — so a "Files" line above it only says the heading again.
        if (! isBrowserForceOpen() && toggleChannel() == null) {
            renderBrowserToggle()
        }

        if (isBrowserOpen()) {
            renderBrowser(selected)
        }

        renderSelected(selected)

        state.editError?.let { error ->
            div {
                css { color = errorColor }
                +error
            }
        }
    }


    // Report's browser toggle: offered only once something is selected, because an empty selection pins the
    // browser open and a control that cannot change anything is noise. Drawn here only while no card header has
    // claimed it — see [toggleChannel].
    private fun ChildrenBuilder.renderBrowserToggle() {
        fileBrowserToggle(state.browserOpen) { toggleBrowser() }
    }


    private fun ChildrenBuilder.renderBrowser(selected: List<FileSelectionEntry>) {
        div {
            css { marginTop = 0.5.em }

            FileBrowser::class.react {
                directory = DataLocation.of(state.browseDirectory ?: state.directory)
                filter = state.filter
                listing = state.listing
                loading = state.loading
                error = state.listingError
                checked = state.checked
                this.selected = selected.map { it.location }.toSet()
                onDirectorySelected = { navigateTo(it.asString()) }
                onFilterChanged = { updateFilter(it) }
                onCheckedChanged = { setState { checked = it } }
                onAdd = { addAll(it) }
                onRemove = { removeAll(it) }
            }
        }
    }


    private fun ChildrenBuilder.renderSelected(selected: List<FileSelectionEntry>) {
        if (selected.isEmpty()) {
            return
        }

        if (isBrowserOpen()) {
            renderSelectedHeading()
        }

        renderSelectedActions(selected)

        FileSelectionTable::class.react {
            entries = selected
            checked = state.selectedChecked
            showDetails = state.showDetails
            perEntryFormat = props.attributeName != legacyPathsAttributeName
            formatCatalog = state.formatCatalog
            onCheckedChanged = { next -> setState { selectedChecked = next } }
            onFormatChanged = { index, value -> editFormat(index, value) }
            onEncodingChanged = { index, value -> editEncoding(index, value) }
        }
    }


    // Worth drawing only while the browser is above it: with the browser hidden the selection is the whole card,
    // and a heading over the only thing present is noise.
    private fun ChildrenBuilder.renderSelectedHeading() {
        div {
            css {
                borderTopWidth = 2.px
                borderTopColor = separatorColor
                borderTopStyle = LineStyle.solid
                marginTop = 1.em
                width = 100.pct
                fontSize = 1.5.em
            }
            +"Selected"
        }
    }


    private fun ChildrenBuilder.renderSelectedActions(selected: List<FileSelectionEntry>) {
        val checkedCount = state.selectedChecked.size

        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                gap = 0.75.em
                flexWrap = FlexWrap.wrap
                marginTop = 0.5.em
            }

            selectionButton(
                label = if (checkedCount == 0) "Remove" else "Remove ($checkedCount)",
                iconName = "material-symbols:do-not-disturb-on-outline",
                enabled = checkedCount != 0,
                title = if (checkedCount == 0) "No files checked" else "Remove checked files"
            ) {
                removeAll(state.selectedChecked.toList())
            }

            selectionButton(
                label = "Up",
                iconName = "material-symbols:arrow-upward",
                enabled = moveChecked(selected, state.selectedChecked, -1) != selected,
                title = "Move checked files earlier"
            ) {
                moveCheckedBy(-1)
            }

            selectionButton(
                label = "Down",
                iconName = "material-symbols:arrow-downward",
                enabled = moveChecked(selected, state.selectedChecked, 1) != selected,
                title = "Move checked files later"
            ) {
                moveCheckedBy(1)
            }

            div { css { flexGrow = number(1.0) } }

            renderDetailsToggle()
        }
    }


    private fun ChildrenBuilder.selectionButton(
        label: String,
        iconName: String,
        enabled: Boolean,
        title: String,
        onAction: () -> Unit
    ) {
        Button {
            variant = ButtonVariant.outlined
            size = Size.small
            disabled = ! enabled
            this.title = title
            sx {
                color = NamedColor.black
                borderColor = Color("#777777")
            }
            onClick = { onAction() }
            icon(iconName) { style = unsafeJso { marginRight = 0.25.em } }
            +label
        }
    }


    // Report's Details toggle: the full path and the per-file format overrides are what make a row wide, and most
    // of the time neither is being read.
    private fun ChildrenBuilder.renderDetailsToggle() {
        val showing = state.showDetails

        Button {
            variant = ButtonVariant.outlined
            size = Size.small
            sx {
                if (showing) {
                    backgroundColor = toggleSelected
                }
                color = NamedColor.black
                borderColor = Color("#777777")
            }
            title = if (showing) "Hide: Details" else "Show: Details"
            onClick = { setState { showDetails = ! showing } }
            icon("material-symbols:more-horiz") {
                style = unsafeJso { marginLeft = (-0.25).em; marginRight = (-0.25).em }
            }
        }
    }
}
