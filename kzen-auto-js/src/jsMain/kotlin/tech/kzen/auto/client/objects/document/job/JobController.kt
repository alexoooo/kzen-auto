package tech.kzen.auto.client.objects.document.job

import emotion.react.css
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.IconButton
import mui.material.Size
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.bridge.InsertionKey
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.ribbon.RibbonController
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.common.util.naming.NextAvailableName
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface JobControllerProps: Props {
    var clientStateGlobal: ClientStateGlobal
    var restClient: ClientRestApi
    var objectStableMapper: ObjectStableMapper
    var mirroredGraphStore: MirroredGraphStore
    var attributeEditorManager: AttributeEditorManager.Wrapper
}


external interface JobControllerState: State {
    var clientState: ClientState?

    // Per-Worker live progress (status + counts + preview teaser), polled from the logic trace store after
    // each run-status change. Null until the first fetch.
    var workerProgress: Map<ObjectLocation, JobWorkerProgress>?

    // On-demand larger sample pulled from a PreviewWorker over its duplex `serve` channel (Worker location ->
    // queried slice). Distinct from the always-on pushed teaser in [workerProgress]. Null until first query.
    var previewDetail: Map<ObjectLocation, JobWorkerProgress>?
}


//---------------------------------------------------------------------------------------------------------------------
// The Job editor: the Worker / Channel graph the user assembles from the ribbon palette (header), each rendered
// as a card with its attribute editors (path, delimiter, channel-reference dropdowns, ...), live status / counts,
// and — for a Preview worker — a live sample table plus an on-demand duplex slice query. Run / Step / Pause come
// from the shared logic ribbon, exactly like Script / Flow. A graphical node-and-edge canvas is deferred (see
// kzen/plans/2026-06-23_job-paradigm.md, M4).
@Suppress("unused")
class JobController(
    props: JobControllerProps
):
    RPureComponent<JobControllerProps, JobControllerState>(props),
    ClientStateGlobal.Observer,
    InsertionGlobal.Subscriber
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation,
        private val ribbonController: RibbonController.Wrapper,
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val restClient: ClientRestApi,
        @Service private val objectStableMapper: ObjectStableMapper,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }


        override fun header(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    ribbonController.child(this) {}
                }
            }
        }


        override fun body(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    JobController::class.react {
                        this.clientStateGlobal = this@Wrapper.clientStateGlobal
                        this.restClient = this@Wrapper.restClient
                        this.objectStableMapper = this@Wrapper.objectStableMapper
                        this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                        this.attributeEditorManager = this@Wrapper.attributeEditorManager
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        // The ribbon (header) publishes the selected insertion archetype on this per-document channel; the body
        // subscribes and inserts it. Reached via the shared DocumentBridge context (see RibbonController).
        installContextType(DocumentBridgeContext)
    }

    private fun insertion(): InsertionGlobal? =
        contextValue<DocumentBridge?>()?.channel(InsertionKey)


    //-----------------------------------------------------------------------------------------------------------------
    private val jobProgressStore by lazy {
        JobProgressStore(props.restClient, props.objectStableMapper)
    }

    // Refetch progress only when the document or the run status changes (mirrors FlowController); during a run
    // the status time advances on each logic-status poll, so this also drives the live progress refresh.
    private var lastFetchKey: String? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun JobControllerState.init(props: JobControllerProps) {
        clientState = null
        workerProgress = null
        previewDetail = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
        insertion()?.subscribe(this)
    }


    override fun componentWillUnmount() {
        insertion()?.unsubscribe(this)
        props.clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        setState {
            this.clientState = clientState
        }

        refreshProgressIfNeeded(clientState)
    }


    private fun refreshProgressIfNeeded(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return

        if (! JobConventions.isJob(documentNotation)) {
            return
        }

        // Key on the run-status time so each logic-status poll during a run refreshes the live progress, and
        // on the document path so opening a Job loads its last run's final progress.
        val logicStatusTime = clientState.clientLogicState.logicStatus?.time
        val fetchKey = "${documentPath.asString()}|$logicStatusTime"
        if (fetchKey == lastFetchKey) {
            return
        }
        lastFetchKey = fetchKey

        val mainLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)
        val workerLocations = workerPaths(documentNotation)
            .map { ObjectLocation(documentPath, it) }

        async {
            val progress = jobProgressStore.fetchWorkerProgress(mainLocation, workerLocations)
            // Value-equality gate (data class + map): skip setState when nothing changed so RPureComponent
            // bails out instead of re-rendering on every (identical) poll.
            if (progress != state.workerProgress) {
                setState {
                    workerProgress = progress
                }
            }
        }

        // Keep any opened larger preview slice live: while the run is active, re-pull each over its duplex
        // serve channel on every poll (the Worker's rolling window means each pull is a fresh sample). When
        // the run ends, drop the live slices so the persisted final teaser shows instead.
        val active = clientState.clientLogicState.logicStatus?.active != null
        if (active) {
            val openedPreviewLocations = (state.previewDetail ?: mapOf()).keys
                .filter { it.documentPath == documentPath }
            if (openedPreviewLocations.isNotEmpty()) {
                async {
                    for (workerLocation in openedPreviewLocations) {
                        refreshPreviewSlice(clientState, workerLocation)
                    }
                }
            }
        }
        else if (state.previewDetail?.isNotEmpty() == true) {
            setState {
                previewDetail = mapOf()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Insert the ribbon-selected archetype into the active Job: a Channel nests under `channels`, anything else
    // (a Worker) under `workers`, appended at the end of the document. Deferred to a coroutine so it runs after
    // the synchronous subscriber-notification loop in InsertionGlobal.setSelected completes, then the selection
    // is cleared so the ribbon button un-highlights.
    override fun onInsertionSelected(action: ObjectLocation) {
        val insertion = insertion()
            ?: return

        async {
            insertArchetype(action)
            insertion.clearSelection()
        }
    }


    override fun onInsertionUnselected() {}


    private suspend fun insertArchetype(archetype: ObjectLocation) {
        val clientState = props.clientStateGlobal.current()
            ?: return
        val graphNotation = clientState.graphStructure().graphNotation

        val documentPath = clientState.navigationRoute.documentPath
            ?: return
        val documentNotation = graphNotation.documents[documentPath]
            ?: return
        if (! JobConventions.isJob(documentNotation)) {
            return
        }

        val attributePath =
            if (JobConventions.isChannelArchetype(graphNotation, archetype)) {
                JobConventions.channelsAttributePath
            }
            else {
                JobConventions.workersAttributePath
            }

        val namePrefix = graphNotation
            .firstAttribute(archetype, AutoConventions.titleAttributePath)
            ?.asString()
            ?: archetype.objectPath.name.value

        val existingNames = documentNotation.objects.notations.map.keys
            .map { it.name }
            .toSet()

        val newName = NextAvailableName
            .find(namePrefix, separator = " ") { ObjectName(it) !in existingNames }
            ?.let { ObjectName(it) }
            ?: AutoConventions.randomAnonymous()

        val newObjectLocation = ObjectLocation(
            documentPath,
            NotationConventions.mainObjectPath.nest(attributePath, newName))

        val insertIndex = documentNotation.objects.notations.map.size

        props.mirroredGraphStore.apply(AddObjectCommand.ofParent(
            newObjectLocation,
            PositionRelation.at(insertIndex),
            archetype.objectPath.name))
    }


    private fun onDelete(objectLocation: ObjectLocation) {
        async {
            props.mirroredGraphStore.apply(RemoveObjectCommand(objectLocation))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun workerPaths(documentNotation: DocumentNotation): List<ObjectPath> {
        return documentNotation.directNestedObjectPaths(
            NotationConventions.mainObjectPath, JobConventions.workersAttributeName)
    }


    private fun channelPaths(documentNotation: DocumentNotation): List<ObjectPath> {
        return documentNotation.directNestedObjectPaths(
            NotationConventions.mainObjectPath, JobConventions.channelsAttributeName)
    }


    private fun isPreviewWorker(documentNotation: DocumentNotation, workerPath: ObjectPath): Boolean {
        val workerIs = documentNotation.objects.notations[workerPath]
            ?.get(NotationConventions.isAttributeName)
            ?.asString()
        return workerIs == "PreviewWorker"
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val clientState = state.clientState
            ?: return

        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return

        if (! JobConventions.isJob(documentNotation)) {
            return
        }

        val workerPaths = workerPaths(documentNotation)
        val channelPaths = channelPaths(documentNotation)

        div {
            css {
                margin = Margin(5.em, 2.em, 2.em, 2.em)
            }

            if (workerPaths.isEmpty() && channelPaths.isEmpty()) {
                div {
                    css {
                        fontSize = 1.25.em
                        marginBottom = 1.em
                    }
                    +"Empty Job — add Workers and Channels from the ribbon above."
                }
            }

            renderChannels(documentPath, documentNotation, channelPaths)
            renderWorkers(documentPath, documentNotation, workerPaths)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderWorkers(
        documentPath: DocumentPath,
        documentNotation: DocumentNotation,
        workerPaths: List<ObjectPath>
    ) {
        h2 { +"Workers" }

        if (workerPaths.isEmpty()) {
            div { +"(none)" }
            return
        }

        for (workerPath in workerPaths) {
            val workerLocation = ObjectLocation(documentPath, workerPath)
            val progress = state.workerProgress?.get(workerLocation)

            objectCard(workerPath) {
                cardHeader(workerPath, workerLocation) {
                    span {
                        css {
                            fontFamily = FontFamily.monospace
                            marginLeft = 0.5.em
                            color = NamedColor.gray
                        }
                        +statusText(progress)
                    }
                }

                renderAttributeEditors(workerLocation)

                if (isPreviewWorker(documentNotation, workerPath)) {
                    renderPreview(workerLocation, progress)
                }
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


    private fun ChildrenBuilder.renderChannels(
        documentPath: DocumentPath,
        documentNotation: DocumentNotation,
        channelPaths: List<ObjectPath>
    ) {
        h2 { +"Channels" }

        if (channelPaths.isEmpty()) {
            div { +"(none)" }
            return
        }

        for (channelPath in channelPaths) {
            val channelLocation = ObjectLocation(documentPath, channelPath)
            val external = JobConventions.isExternalChannel(documentNotation, channelPath)

            objectCard(channelPath) {
                cardHeader(channelPath, channelLocation) {
                    if (external) {
                        span {
                            css {
                                marginLeft = 0.5.em
                                color = NamedColor.gray
                            }
                            +"(external)"
                        }
                    }
                }

                renderAttributeEditors(channelLocation)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The live sample for a Preview worker: the always-on pushed teaser ([workerProgress]), replaced by a larger
    // on-demand slice once the user queries the Worker over its duplex `serve` channel ([previewDetail]).
    private fun ChildrenBuilder.renderPreview(
        workerLocation: ObjectLocation,
        progress: JobWorkerProgress?
    ) {
        val detail = state.previewDetail?.get(workerLocation)
        val shown = detail ?: progress
        val active = state.clientState?.clientLogicState?.isActive() ?: false

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
                onClick = { queryPreview(workerLocation) }
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


    // Show / start the larger live preview slice for a Preview worker: pulls it once now; while the run stays
    // active, refreshProgressIfNeeded keeps re-pulling it each poll (and clears it once the run ends).
    private fun queryPreview(workerLocation: ObjectLocation) {
        val clientState = state.clientState
            ?: return
        async {
            refreshPreviewSlice(clientState, workerLocation)
        }
    }


    // Pull one Preview worker's current slice over its duplex `serve` channel and store it, value-equality
    // gated so an unchanged slice doesn't re-render.
    private suspend fun refreshPreviewSlice(clientState: ClientState, workerLocation: ObjectLocation) {
        val parsed = queryPreviewSlice(clientState, workerLocation)
            ?: return

        if (parsed == state.previewDetail?.get(workerLocation)) {
            return
        }

        val updated = (state.previewDetail ?: mapOf()).plus(workerLocation to parsed)
        setState {
            previewDetail = updated
        }
    }


    // Issue an `offset` / `limit` slice query to a Preview worker over its (external) duplex `serve` channel,
    // via the running logic's request subscriber — the browser -> Worker request/reply path. The reply has the
    // same shape as the pushed teaser.
    private suspend fun queryPreviewSlice(
        clientState: ClientState,
        workerLocation: ObjectLocation
    ): JobWorkerProgress? {
        val logicRunInfo = clientState.clientLogicState.logicStatus?.active
            ?: return null

        val serveReference = clientState.graphStructure().graphNotation
            .firstAttribute(workerLocation, AttributePath.ofName(AttributeName("serve")))
            ?.asString()
            ?: return null
        val channelName = serveReference.substringAfterLast("/")

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
    // A bordered card per Worker / Channel, keyed by its object path so its attribute-editor subtree reconciles
    // independently of its siblings.
    private fun ChildrenBuilder.objectCard(
        objectPath: ObjectPath,
        content: ChildrenBuilder.() -> Unit
    ) {
        div {
            key = Key(objectPath.asString())
            css {
                marginBottom = 0.75.em
                padding = Padding(0.5.em, 0.75.em, 0.5.em, 0.75.em)
                border = Border(1.px, LineStyle.solid, Color("#c4c4c4"))
                borderRadius = 3.px
                backgroundColor = NamedColor.white
                maxWidth = 40.em
            }
            content()
        }
    }


    private fun ChildrenBuilder.cardHeader(
        objectPath: ObjectPath,
        objectLocation: ObjectLocation,
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
                +objectPath.name.value
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
                onClick = { onDelete(objectLocation) }
                icon("material-symbols:delete-outline") {}
            }
        }
    }


    // Render an editor for each non-managed attribute via the shared AttributeEditorManager — scalars (path,
    // delimiter, buffer, ...) fall to the default value editor; channel-reference attributes dispatch to
    // SelectChannelEditor via their `editor:` metadata. Attributes are in fixed metadata order, so they reconcile
    // by position within this (path-keyed) card.
    private fun ChildrenBuilder.renderAttributeEditors(objectLocation: ObjectLocation) {
        val objectMetadata: ObjectMetadata = state.clientState
            ?.graphStructure()
            ?.graphMetadata
            ?.objectMetadata
            ?.get(objectLocation)
            ?: return

        for ((attributeName, _) in objectMetadata.attributes.map) {
            if (AutoConventions.isManaged(attributeName)) {
                continue
            }

            div {
                css {
                    marginBottom = 0.25.em
                }
                renderAttributeEditor(objectLocation, attributeName)
            }
        }
    }


    private fun ChildrenBuilder.renderAttributeEditor(
        objectLocation: ObjectLocation,
        attributeName: AttributeName
    ) {
        props.attributeEditorManager.child(this) {
            this.objectLocation = objectLocation
            this.attributeName = attributeName
        }
    }
}
