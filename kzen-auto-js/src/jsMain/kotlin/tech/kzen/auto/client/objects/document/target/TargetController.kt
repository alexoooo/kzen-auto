package tech.kzen.auto.client.objects.document.target

import emotion.react.css
import js.objects.unsafeJso
import kotlinx.browser.window
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.select
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.script.display.image.pngUrl
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.target.TargetDocument
import tech.kzen.auto.common.objects.document.target.TargetLocateResult
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceEntry
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.model.structure.resource.ResourceName
import tech.kzen.lib.common.model.structure.resource.ResourceNesting
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.platform.DateTimeUtils
import tech.kzen.lib.platform.IoUtils
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface TargetControllerProps: Props {
    var mirroredGraphStore: MirroredGraphStore
    var navigationGlobal: NavigationGlobal
    var restClient: ClientRestApi
}


external interface TargetControllerState: State {
    var documentPath: DocumentPath?
    var parameters: RequestParams?
    var graphStructure: GraphStructure?

    var source: String?
    var captureDelaySeconds: Int?

    var screenshotPng: ByteArray?
    var screenshotDataUrl: String?
    var screenshotError: String?
    var requestingScreenshot: Boolean?

    var locateResult: TargetLocateResult?
    var locating: Boolean?
    var locateError: String?

    var traceScreenshots: List<BinaryExecutionValue>?
    var traceError: String?
    var requestingTrace: Boolean?
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Two routed sub-pages: View (how do the captured patches match, live) and Add (capture a new
 * patch), selected by the `section` hash param so a refresh keeps the page and the tabs are
 * real links. The screenshot can come from the desktop (with an optional delay to alt-tab) or
 * from a run's browser trace (bit-identical to what matching saw).
 */
@Suppress("unused")
class TargetController(
    props: TargetControllerProps
):
    RPureComponent<TargetControllerProps, TargetControllerState>(props),
    NavigationGlobal.Observer,
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val sectionKey = "section"
        private const val sectionView = "view"
        private const val sectionAdd = "add"

        private const val sourceScreen = "screen"
        private const val sourceBrowser = "browser"

        private val captureDelayOptions = listOf(0, 3, 10)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val navigationGlobal: NavigationGlobal,
        @Service private val restClient: ClientRestApi
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }


        override fun header(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {}
            }
        }


        override fun body(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    TargetController::class.react {
                        mirroredGraphStore = this@Wrapper.mirroredGraphStore
                        navigationGlobal = this@Wrapper.navigationGlobal
                        restClient = this@Wrapper.restClient
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun TargetControllerState.init(props: TargetControllerProps) {
        documentPath = null
        parameters = null
        graphStructure = null

        source = sourceScreen
        captureDelaySeconds = 0

        screenshotPng = null
        screenshotDataUrl = null
        screenshotError = null
        requestingScreenshot = false

        locateResult = null
        locating = false
        locateError = null

        traceScreenshots = null
        traceError = null
        requestingTrace = false
    }


    override fun componentDidMount() {
        async {
            props.mirroredGraphStore.observe(this)
            props.navigationGlobal.observe(this)
        }
    }


    override fun componentWillUnmount() {
        props.mirroredGraphStore.unobserve(this)
        props.navigationGlobal.unobserve(this)
    }


    override fun componentDidUpdate(
        prevProps: TargetControllerProps,
        prevState: TargetControllerState,
        snapshot: Any
    ) {
        if (state.documentPath == null) {
            return
        }

        val screenshotPending =
            state.screenshotPng == null &&
            state.screenshotError == null &&
            state.requestingScreenshot != true

        if (screenshotPending) {
            when (state.source) {
                sourceScreen ->
                    doRequestScreenshot()

                sourceBrowser ->
                    if (state.traceScreenshots == null &&
                            state.traceError == null &&
                            state.requestingTrace != true) {
                        doRequestTraceScreenshots()
                    }
            }
        }

        val locatePending =
            state.screenshotPng != null &&
            state.locateResult == null &&
            state.locateError == null &&
            state.locating != true &&
            hasCrops()

        if (locatePending) {
            doLocate()
        }
    }


    private fun hasCrops(): Boolean {
        val documentPath = state.documentPath
            ?: return false

        val resources = state
            .graphStructure?.graphNotation?.documents?.get(documentPath)?.resources
            ?: return false

        return resources.digests.isNotEmpty()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(
        documentPath: DocumentPath?,
        parameters: RequestParams
    ) {
        setState {
            this.documentPath = documentPath
            this.parameters = parameters
        }
    }


    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        if ((event is DeletedDocumentEvent || event is RenamedDocumentRefactorEvent) &&
                event.documentPath == state.documentPath) {
            return
        }

        val cropsChanged = event.documentPath == state.documentPath

        setState {
            this.graphStructure = graphDefinition.graphStructure

            if (cropsChanged) {
                locateResult = null
                locateError = null
            }
        }
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        setState {
            this.graphStructure = graphDefinitionAttempt.graphStructure
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun doRequestScreenshot() {
        setState {
            requestingScreenshot = true
        }

        val delayMillis = 1000 * (state.captureDelaySeconds ?: 0)

        window.setTimeout({
            async {
                val result = props.restClient.performDetached(
                    TargetDocument.screenshotTakerLocation)

                when (result) {
                    is ExecutionSuccess -> {
                        val screenshotBytes = (result.value as BinaryExecutionValue).value
                        applyScreenshot(screenshotBytes)
                    }

                    is ExecutionFailure -> {
                        setState {
                            screenshotError = result.errorMessage
                            requestingScreenshot = false
                        }
                    }
                }
            }
        }, delayMillis)
    }


    private fun doRequestTraceScreenshots() {
        setState {
            requestingTrace = true
            requestingScreenshot = true
        }

        async {
            val screenshots = fetchTraceScreenshots()

            if (screenshots == null) {
                setState {
                    requestingTrace = false
                    requestingScreenshot = false
                }
                return@async
            }

            setState {
                traceScreenshots = screenshots
                requestingTrace = false
            }

            val latest = screenshots.lastOrNull()
            if (latest != null) {
                applyScreenshot(latest.value)
            }
            else {
                setState {
                    traceError = "No screenshots in the most recent run"
                    requestingScreenshot = false
                }
            }
        }
    }


    /**
     * Latest traced run's browser screenshots, oldest first; null (with traceError set) when
     * there is nothing to show.
     */
    private suspend fun fetchTraceScreenshots(): List<BinaryExecutionValue>? {
        val tracedResult = traceQuery(
            CommonRestApi.paramAction to LogicConventions.actionTraced)
            ?: return null

        @Suppress("UNCHECKED_CAST")
        val tracedDocuments = tracedResult.value.get() as List<String>

        if (tracedDocuments.isEmpty()) {
            setState {
                traceError = "No traced runs (run a Script with browser steps first)"
            }
            return null
        }

        val tracedDocument = DocumentPath.parse(tracedDocuments.first())

        val mostRecentResult = traceQuery(
            CommonRestApi.paramAction to LogicConventions.actionMostRecent,
            LogicConventions.paramSubDocumentPath to tracedDocument.asString(),
            LogicConventions.paramSubObjectPath to NotationConventions.mainObjectPath.asString())
            ?: return null

        @Suppress("UNCHECKED_CAST")
        val mostRecentCollection = mostRecentResult.value.get() as Map<String, String>?

        if (mostRecentCollection == null) {
            setState {
                traceError = "No run found for: $tracedDocument"
            }
            return null
        }

        val runExecutionId = LogicConventions.runExecutionFromCollection(mostRecentCollection)

        val snapshotResult = traceQuery(
            CommonRestApi.paramAction to LogicConventions.actionLookupRun,
            CommonRestApi.paramRunId to runExecutionId.logicRunId.value,
            LogicConventions.paramQuery to LogicTraceQuery(LogicTracePath.root).asString())
            ?: return null

        @Suppress("UNCHECKED_CAST")
        val snapshotCollection = snapshotResult.value.get() as Map<String, Map<String, Any>>

        return snapshotCollection
            .values
            .map { LogicTraceEntry.ofCollection(it) }
            .filter { it.value is BinaryExecutionValue }
            .sortedBy { it.sequence }
            .map { it.value as BinaryExecutionValue }
    }


    private suspend fun traceQuery(
        vararg parameters: Pair<String, String>
    ): ExecutionSuccess? {
        val result = props.restClient.performDetached(
            LogicConventions.logicTraceEndpointLocation,
            *parameters)

        return when (result) {
            is ExecutionSuccess ->
                result

            is ExecutionFailure -> {
                setState {
                    traceError = result.errorMessage
                }
                null
            }
        }
    }


    private fun applyScreenshot(screenshotBytes: ByteArray) {
        val base64 = IoUtils.base64Encode(screenshotBytes)

        setState {
            screenshotPng = screenshotBytes
            screenshotDataUrl = "data:image/png;base64,$base64"
            screenshotError = null
            requestingScreenshot = false

            locateResult = null
            locateError = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun doLocate() {
        val documentPath = state.documentPath
            ?: return
        val screenshotPng = state.screenshotPng
            ?: return

        setState {
            locating = true
        }

        async {
            val result = props.restClient.performDetached(
                TargetDocument.targetLocateLocation,
                screenshotPng,
                TargetDocument.paramTarget to documentPath.asString())

            when (result) {
                is ExecutionSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    val locateCollection = result.value.get() as Map<String, Any>

                    setState {
                        locateResult = TargetLocateResult.ofCollection(locateCollection)
                        locating = false
                    }
                }

                is ExecutionFailure -> {
                    setState {
                        locateError = result.errorMessage
                        locating = false
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRefresh() {
        setState {
            screenshotPng = null
            screenshotDataUrl = null
            screenshotError = null

            locateResult = null
            locateError = null

            traceScreenshots = null
            traceError = null
        }
    }


    private fun onSourceChange(source: String) {
        if (source == state.source) {
            return
        }

        setState {
            this.source = source
        }
        onRefresh()
    }


    private fun onCaptureDelayChange(delaySeconds: Int) {
        setState {
            captureDelaySeconds = delaySeconds
        }
    }


    private fun onTraceScreenshotSelect(screenshot: BinaryExecutionValue) {
        applyScreenshot(screenshot.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRemove(resourcePath: ResourcePath) {
        async {
            props.mirroredGraphStore.apply(RemoveResourceCommand(
                ResourceLocation(
                    state.documentPath!!,
                    resourcePath)
            ))
        }
    }


    private fun onSave(cropPng: ByteArray) {
        val documentPath = state.documentPath
            ?: return

        async {
            props.mirroredGraphStore.apply(AddResourceCommand(
                ResourceLocation(
                    documentPath,
                    ResourcePath(
                        ResourceName(DateTimeUtils.filenameTimestamp() + ".png"),
                        ResourceNesting.empty)),
                ImmutableByteArray.wrap(cropPng)
            ))

            // Jump to View so the new crop's match is visible right away
            props.navigationGlobal.parameterize(
                (state.parameters ?: RequestParams.empty).set(sectionKey, sectionView))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun activeSection(): String {
        val requested = state.parameters?.get(sectionKey)

        if (requested == sectionView || requested == sectionAdd) {
            return requested
        }

        return if (hasCrops()) { sectionView } else { sectionAdd }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val documentPath = state.documentPath
            ?: return

        val graphStructure = state.graphStructure
            ?: return

        val documentNotation = graphStructure.graphNotation.documents[documentPath]
            ?: return
        val resources = documentNotation.resources
            ?: return

        val section = activeSection()

        div {
            css {
                padding = Padding(1.em, 1.em, 0.5.em, 1.em)
            }

            renderSectionTabs(documentPath, section)
            renderSourceControls()
            renderStatus()
        }

        when (section) {
            sectionView ->
                TargetView::class.react {
                    this.documentPath = documentPath
                    this.resources = resources
                    restClient = props.restClient

                    screenshotDataUrl = state.screenshotDataUrl
                    locateResult = state.locateResult
                    locating = state.locating

                    onRemove = ::onRemove
                }

            sectionAdd -> {
                val screenshotDataUrl = state.screenshotDataUrl
                if (screenshotDataUrl != null) {
                    TargetAdd::class.react {
                        this.screenshotDataUrl = screenshotDataUrl
                        onSave = ::onSave
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderSectionTabs(
        documentPath: DocumentPath,
        activeSection: String
    ) {
        div {
            css {
                marginBottom = 0.5.em
            }

            renderSectionTab(documentPath, activeSection, sectionView, "View")
            renderSectionTab(documentPath, activeSection, sectionAdd, "Add")
        }
    }


    private fun ChildrenBuilder.renderSectionTab(
        documentPath: DocumentPath,
        activeSection: String,
        section: String,
        label: String
    ) {
        val parameters = state.parameters ?: RequestParams.empty
        val active = section == activeSection

        a {
            css {
                display = Display.inlineBlock
                padding = Padding(0.25.em, 1.em)
                marginRight = 0.5.em
                color = Globals.inherit
                textDecoration =
                    if (active) { None.none }
                    else { Globals.initial }
                fontWeight =
                    if (active) { FontWeight.bold }
                    else { FontWeight.normal }
                backgroundColor =
                    if (active) { NamedColor.white }
                    else { Color("transparent") }
                borderRadius = 3.px
            }

            draggable = false
            href = NavigationRoute(
                documentPath,
                parameters.set(sectionKey, section)
            ).toFragment()

            +label
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderSourceControls() {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                gap = 0.5.em
            }

            span {
                +"Screenshot:"
            }

            select {
                value = state.source ?: sourceScreen
                onChange = {
                    onSourceChange(it.currentTarget.value)
                }

                option {
                    value = sourceScreen
                    +"Screen"
                }
                option {
                    value = sourceBrowser
                    +"Browser (latest run)"
                }
            }

            if (state.source == sourceScreen) {
                span {
                    +"Delay:"
                }

                select {
                    value = (state.captureDelaySeconds ?: 0).toString()
                    onChange = {
                        onCaptureDelayChange(it.currentTarget.value.toInt())
                    }

                    for (delayOption in captureDelayOptions) {
                        option {
                            value = delayOption.toString()
                            +when (delayOption) {
                                0 -> "none"
                                else -> "$delayOption seconds"
                            }
                        }
                    }
                }
            }

            renderRefresh()
        }

        val traceScreenshots = state.traceScreenshots
        if (state.source == sourceBrowser && !traceScreenshots.isNullOrEmpty()) {
            renderTraceStrip(traceScreenshots)
        }
    }


    private fun ChildrenBuilder.renderRefresh() {
        Button {
            sx {
                backgroundColor = NamedColor.white
            }
            variant = ButtonVariant.outlined
            size = Size.small

            onClick = { onRefresh() }

            icon("material-symbols:refresh") {
                style = unsafeJso {
                    marginRight = 0.25.em
                }
            }
            +"Refresh"
        }
    }


    private fun ChildrenBuilder.renderTraceStrip(
        traceScreenshots: List<BinaryExecutionValue>
    ) {
        div {
            css {
                display = Display.flex
                gap = 0.5.em
                marginTop = 0.5.em
                overflowX = Auto.auto
            }

            for (screenshot in traceScreenshots) {
                img {
                    css {
                        height = 5.em
                        cursor = Cursor.pointer
                        border = Border(1.px, LineStyle.solid, NamedColor.gray)
                    }

                    src = pngUrl(screenshot)

                    onClick = {
                        onTraceScreenshotSelect(screenshot)
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderStatus() {
        val statusMessages = listOfNotNull(
            state.screenshotError,
            state.traceError,
            state.locateError?.let { "Matching failed: $it" })

        if (statusMessages.isNotEmpty()) {
            for (statusMessage in statusMessages) {
                div {
                    css {
                        marginTop = 0.5.em
                        color = NamedColor.firebrick
                    }
                    +statusMessage
                }
            }
        }
        else if (state.requestingScreenshot == true) {
            div {
                css {
                    marginTop = 0.5.em
                    color = NamedColor.gray
                }
                +"Taking screenshot…"
            }
        }
    }
}
