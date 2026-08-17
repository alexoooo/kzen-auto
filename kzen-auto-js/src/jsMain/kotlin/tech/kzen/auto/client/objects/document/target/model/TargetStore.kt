package tech.kzen.auto.client.objects.document.target.model

import kotlinx.browser.window
import tech.kzen.auto.client.objects.document.target.TargetSection
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.auto.common.objects.document.target.TargetDocument
import tech.kzen.auto.common.objects.document.target.TargetLocateResult
import tech.kzen.auto.common.objects.document.target.model.TargetFetch
import tech.kzen.auto.common.objects.document.target.model.TargetFetchPlan
import tech.kzen.auto.common.objects.document.target.model.TargetScreenshotSource
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.BinaryHandleExecutionValue
import tech.kzen.lib.common.exec.BinaryValue
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceEntry
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddResourceCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveResourceCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.model.structure.resource.ResourceName
import tech.kzen.lib.common.model.structure.resource.ResourceNesting
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.platform.DateTimeUtils


/**
 * Owns the Target document's editor state and the fetches that fill it: the working screenshot (a desktop
 * capture, or a frame of the latest traced run's browser strip) and the match of that screenshot against the
 * document's captured patches.
 *
 * The fetches are scheduled, not hand-sequenced: every state change ends in [advance], which asks
 * [TargetFetchPlan] what the current channel phases owe next. So arming a fetch means putting its channel back
 * to [TargetFetch.Idle] — there is no separate "should I start one" condition to keep in step with the state.
 */
class TargetStore(
    private val clientStateGlobal: ClientStateGlobal,
    private val mirroredGraphStore: MirroredGraphStore,
    private val navigationGlobal: NavigationGlobal,
    private val restClient: ClientRestApi
):
    ClientStateGlobal.DocumentScopedObserver
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val millisPerSecond = 1000

        private const val noTracedRunsMessage = "No traced runs (run a Script with browser steps first)"
        private const val noTraceScreenshotsMessage = "No screenshots in the most recent run"
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onTargetState(targetState: TargetState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var mounted = false
    private var state: TargetState? = null

    // Each fetch outlives the state that armed it — the desktop capture waits out the user's alt-tab delay, and
    // both trace calls plus the match are round trips — so a superseded answer could otherwise land on top of a
    // newer one (switch source mid-delay and the abandoned screen capture arrives against the browser strip).
    // Anything that re-arms a channel bumps this, and an answer whose epoch is no longer current is dropped.
    // Matches ScriptStore's validationEpoch; the scheduled capture is left to fire and no-op rather than
    // cancelled, since the epoch check is what makes it harmless either way.
    private var fetchEpoch = 0


    //-----------------------------------------------------------------------------------------------------------------
    fun observe(observer: Observer) {
        observers.add(observer)
        state?.let { observer.onTargetState(it) }
    }


    fun unobserve(observer: Observer) {
        val removed = observers.remove(observer)
        check(removed) { "Not found: $observer" }
    }


    private fun publish(nextState: TargetState) {
        for (observer in observers.toList()) {
            observer.onTargetState(nextState)
        }
    }


    fun didMount() {
        mounted = true
        async {
            // willUnmount runs synchronously, so registering after it would leak this observer.
            if (mounted) {
                clientStateGlobal.observe(this)
            }
        }
    }


    fun willUnmount() {
        mounted = false
        state = null
        fetchEpoch++
        clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        if (!mounted) {
            return
        }

        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return

        // Navigation publishes before the departing controller unmounts, so a non-Target document (or this one
        // mid-delete) reaches here; leaving the state untouched keeps the editor showing what it was editing.
        if (!TargetDocument.isTarget(documentNotation)) {
            return
        }

        val parameters = clientState.navigationRoute.requestParams
        val previous = state

        val nextState = when {
            previous == null ->
                TargetState.initial(documentPath, parameters, documentNotation)

            // Another Target document has its own patches, so the match is re-armed; the working screenshot and
            // the capture settings are the user's and stay as they left them.
            previous.documentPath != documentPath ->
                previous.copy(
                    documentPath = documentPath,
                    parameters = parameters,
                    documentNotation = documentNotation,
                    locate = TargetFetch.Idle)

            // A patch added or removed, or the tolerance moved: the standing match no longer describes the
            // document, so it is re-run rather than left on screen.
            documentNotation != previous.documentNotation ->
                previous.copy(
                    parameters = parameters,
                    documentNotation = documentNotation,
                    locate = TargetFetch.Idle)

            else ->
                previous.copy(parameters = parameters)
        }

        updateIfChanged(nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun update(updater: (TargetState) -> TargetState) {
        val current = state
            ?: return
        updateIfChanged(updater(current))
    }


    private fun updateIfChanged(nextState: TargetState) {
        val previous = state
        if (previous == nextState) {
            return
        }

        if (previous != null && nextState.rearms(previous)) {
            fetchEpoch++
        }

        state = nextState
        publish(nextState)
        advance()
    }


    /** Applies a fetch's answer only while that fetch is still the current one — see [fetchEpoch]. */
    private fun settle(epoch: Int, updater: (TargetState) -> TargetState) {
        if (epoch != fetchEpoch) {
            return
        }
        update(updater)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun advance() {
        val current = state
            ?: return

        val action = TargetFetchPlan.next(
            current.source,
            current.screenshot.phase,
            current.trace.phase,
            current.locate.phase,
            current.hasCrops)

        when (action) {
            TargetFetchPlan.Action.None -> {}
            TargetFetchPlan.Action.Screenshot -> requestScreenshot(current.captureDelaySeconds)
            TargetFetchPlan.Action.TraceScreenshots -> requestTraceScreenshots()
            TargetFetchPlan.Action.Locate -> requestLocate(current)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun requestScreenshot(captureDelaySeconds: Int) {
        update { it.copy(screenshot = TargetFetch.Requesting) }
        val epoch = fetchEpoch

        window.setTimeout({
            async {
                if (epoch != fetchEpoch) {
                    return@async
                }

                when (val result = restClient.performDetached(TargetDocument.screenshotTakerLocation)) {
                    is ExecutionSuccess ->
                        applyScreenshot((result.value as BinaryExecutionValue).value, epoch)

                    is ExecutionFailure ->
                        settle(epoch) { it.copy(screenshot = TargetFetch.Failed(result.errorMessage)) }
                }
            }
        }, millisPerSecond * captureDelaySeconds)
    }


    private fun requestTraceScreenshots() {
        // The strip's newest frame becomes the working screenshot, so both channels go in flight together and
        // the "Taking screenshot…" indicator covers the whole of it.
        update { it.copy(trace = TargetFetch.Requesting, screenshot = TargetFetch.Requesting) }
        val epoch = fetchEpoch

        async {
            val screenshots = fetchTraceScreenshots(epoch)

            // The screenshot channel goes back to idle on either failure, so a source switch can re-arm it.
            if (screenshots == null) {
                // fetchTraceScreenshots already settled the strip channel with the reason.
                settle(epoch) { it.copy(screenshot = TargetFetch.Idle) }
                return@async
            }

            if (screenshots.isEmpty()) {
                settle(epoch) {
                    it.copy(
                        trace = TargetFetch.Failed(noTraceScreenshotsMessage),
                        screenshot = TargetFetch.Idle)
                }
                return@async
            }

            settle(epoch) { it.copy(trace = TargetFetch.Loaded(screenshots)) }
            applyTraceScreenshot(screenshots.last(), epoch)
        }
    }


    /** Latest traced run's browser screenshots, oldest first; null once the strip channel carries the reason. */
    private suspend fun fetchTraceScreenshots(epoch: Int): List<BinaryValue>? {
        val tracedResult = traceQuery(
            epoch,
            CommonRestApi.paramAction to LogicConventions.actionTraced)
            ?: return null

        @Suppress("UNCHECKED_CAST")
        val tracedDocuments = tracedResult.value.get() as List<String>

        if (tracedDocuments.isEmpty()) {
            settle(epoch) { it.copy(trace = TargetFetch.Failed(noTracedRunsMessage)) }
            return null
        }

        val tracedDocument = DocumentPath.parse(tracedDocuments.first())

        val mostRecentResult = traceQuery(
            epoch,
            CommonRestApi.paramAction to LogicConventions.actionMostRecent,
            LogicConventions.paramSubDocumentPath to tracedDocument.asString(),
            LogicConventions.paramSubObjectPath to NotationConventions.mainObjectPath.asString())
            ?: return null

        @Suppress("UNCHECKED_CAST")
        val mostRecentCollection = mostRecentResult.value.get() as Map<String, String>?

        if (mostRecentCollection == null) {
            settle(epoch) { it.copy(trace = TargetFetch.Failed("No run found for: $tracedDocument")) }
            return null
        }

        val runExecutionId = LogicConventions.runExecutionFromCollection(mostRecentCollection)

        val snapshotResult = traceQuery(
            epoch,
            CommonRestApi.paramAction to LogicConventions.actionLookupRun,
            CommonRestApi.paramRunId to runExecutionId.logicRunId.value,
            LogicConventions.paramQuery to LogicTraceQuery(LogicTracePath.root).asString())
            ?: return null

        @Suppress("UNCHECKED_CAST")
        val snapshotCollection = snapshotResult.value.get() as Map<String, Map<String, Any>>

        return snapshotCollection
            .values
            .map { LogicTraceEntry.ofCollection(it) }
            .sortedBy { it.sequence }
            .mapNotNull { traceScreenshot(it.value) }
    }


    /**
     * A browser step's screenshot rides its step trace as the `detail` binary; other trace entries (run-root
     * index, non-browser steps) carry no screenshot.
     */
    private fun traceScreenshot(value: ExecutionValue): BinaryValue? {
        if (value is BinaryValue) {
            return value
        }

        return StepTrace.ofExecutionValueOrNull(value)?.detail as? BinaryValue
    }


    private suspend fun traceQuery(
        epoch: Int,
        vararg parameters: Pair<String, String>
    ): ExecutionSuccess? {
        val result = restClient.performDetached(
            LogicConventions.logicTraceEndpointLocation,
            *parameters)

        return when (result) {
            is ExecutionSuccess ->
                result

            is ExecutionFailure -> {
                settle(epoch) { it.copy(trace = TargetFetch.Failed(result.errorMessage)) }
                null
            }
        }
    }


    /**
     * A live [BinaryExecutionValue] carries the bytes inline; a content-addressed handle carries none, so the
     * blob is fetched once from the trace-binary endpoint — matching sends the actual PNG to the server.
     */
    private suspend fun applyTraceScreenshot(screenshot: BinaryValue, epoch: Int) {
        val png = when (screenshot) {
            is BinaryExecutionValue ->
                screenshot.value

            is BinaryHandleExecutionValue ->
                restClient.logicTraceBinaryBytes(screenshot.run, screenshot.hash)
        }
        applyScreenshot(png, epoch)
    }


    private fun applyScreenshot(png: ByteArray, epoch: Int) {
        settle(epoch) {
            it.copy(
                screenshot = TargetFetch.Loaded(TargetScreenshot(png)),
                // A different screenshot means a different match, and re-arming it supersedes any match still
                // in flight against the previous one.
                locate = TargetFetch.Idle)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun requestLocate(current: TargetState) {
        val png = current.screenshot.valueOrNull?.png
            ?: return
        val documentPath = current.documentPath

        update { it.copy(locate = TargetFetch.Requesting) }
        val epoch = fetchEpoch

        async {
            val result = restClient.performDetached(
                TargetDocument.targetLocateLocation,
                png,
                TargetDocument.paramTarget to documentPath.asString())

            when (result) {
                is ExecutionSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    val locateCollection = result.value.get() as Map<String, Any>

                    settle(epoch) {
                        it.copy(locate = TargetFetch.Loaded(TargetLocateResult.ofCollection(locateCollection)))
                    }
                }

                is ExecutionFailure ->
                    settle(epoch) { it.copy(locate = TargetFetch.Failed(result.errorMessage)) }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun refresh() {
        update { it.rearmed() }
    }


    fun setSource(source: TargetScreenshotSource) {
        update {
            if (it.source == source) {
                it
            }
            else {
                it.copy(source = source).rearmed()
            }
        }
    }


    fun setCaptureDelaySeconds(captureDelaySeconds: Int) {
        update { it.copy(captureDelaySeconds = captureDelaySeconds) }
    }


    fun selectTraceScreenshot(screenshot: BinaryValue) {
        // The user picked the frame, so it wins over anything already in flight.
        val epoch = ++fetchEpoch
        async {
            applyTraceScreenshot(screenshot, epoch)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun removeCrop(resourcePath: ResourcePath) {
        val documentPath = state?.documentPath
            ?: return

        async {
            mirroredGraphStore.apply(RemoveResourceCommand(
                ResourceLocation(documentPath, resourcePath)))
        }
    }


    fun setTolerance(tolerance: Double) {
        val documentPath = state?.documentPath
            ?: return

        // The resulting notation change re-arms the match (see onClientState), so the overlay re-locates at the
        // new tolerance right away.
        async {
            mirroredGraphStore.apply(UpsertAttributeCommand(
                ObjectLocation(documentPath, NotationConventions.mainObjectPath),
                TargetDocument.toleranceAttributeName,
                ScalarAttributeNotation(tolerance.toString())))
        }
    }


    fun saveCrop(cropPng: ByteArray) {
        val current = state
            ?: return

        async {
            mirroredGraphStore.apply(AddResourceCommand(
                ResourceLocation(
                    current.documentPath,
                    ResourcePath(
                        ResourceName(DateTimeUtils.filenameTimestamp() + ".png"),
                        ResourceNesting.empty)),
                ImmutableByteArray.wrap(cropPng)))

            // Jump to View so the new crop's match is visible right away
            navigationGlobal.parameterize(
                current.parameters.set(TargetSection.parameterKey, TargetSection.view))
        }
    }
}
