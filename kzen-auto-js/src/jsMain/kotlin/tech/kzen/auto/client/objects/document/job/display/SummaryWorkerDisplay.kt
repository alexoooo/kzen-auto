package tech.kzen.auto.client.objects.document.job.display

import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.objects.document.job.JobServeChannelResolver
import tech.kzen.auto.client.objects.document.job.JobSummaryStore
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore


//---------------------------------------------------------------------------------------------------------------------
external interface SummaryWorkerDisplayProps: WorkerDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper
    var clientStateGlobal: ClientStateGlobal
    var restClient: ClientRestApi
    var mirroredGraphStore: MirroredGraphStore
}


//---------------------------------------------------------------------------------------------------------------------
// Display for a summary-serving Worker (SummaryWorker): the default card, plus a background poll that pulls this
// Worker's live TableSummary over its duplex `serve` channel and writes its OWN entry into the document-scoped
// JobSummaryStore (reached through the DocumentBridge). The value-set-filter / pivot editors downstream observe
// that store to source a column's distinct values — they are unchanged; only the producer poll moved here from
// JobController, so the generic controller carries no summary awareness (see CC-17). The store is self-constructing
// (JobSummaryStore.Key.create), so no owner needs to provide it; this card owns only its own entry, removing it on
// unmount (Worker deleted). Kept across a run's end so a filter can still be configured against the last values.
@Suppress("unused")
class SummaryWorkerDisplay(
    props: SummaryWorkerDisplayProps
):
    RPureComponent<SummaryWorkerDisplayProps, State>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        private val attributeViewManager: AttributeViewManager.Wrapper,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val restClient: ClientRestApi,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        WorkerDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: WorkerDisplayProps.() -> Unit) {
            SummaryWorkerDisplay::class.react {
                this.attributeEditorManager = this@Wrapper.attributeEditorManager
                this.attributeViewManager = this@Wrapper.attributeViewManager
                this.clientStateGlobal = this@Wrapper.clientStateGlobal
                this.restClient = this@Wrapper.restClient
                this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    // Refetch only when the run status advances (mirrors JobController's fetch-key gate); a var, not state.
    private var lastSummaryFetchKey: String? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
        // Drop this Worker's own entry (it was deleted / the document switched). lookup, not channel, so an
        // unmount that never wrote doesn't needlessly construct the store.
        contextValue<DocumentBridge?>()?.lookup(JobSummaryStore.Key)?.remove(props.common.objectLocation)
    }


    // Keep this Worker's live TableSummary fresh while the run is active, so the downstream filter / pivot editors
    // can source a column's distinct values from it. Kept (not cleared) once the run ends.
    override fun onClientState(clientState: ClientState) {
        if (! clientState.clientLogicState.isActive()) {
            return
        }

        val fetchKey = clientState.clientLogicState.logicStatus?.time.toString()
        if (fetchKey == lastSummaryFetchKey) {
            return
        }
        lastSummaryFetchKey = fetchKey

        async {
            refreshSummary()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun refreshSummary() {
        val clientState = props.clientStateGlobal.current()
            ?: return
        val summary = querySummary(clientState)
            ?: return
        // Self-constructing channel: whichever card / editor touches the key first creates the one document-scoped
        // store; this card writes only its own entry (value-gated inside put).
        contextValue<DocumentBridge?>()?.channel(JobSummaryStore.Key)?.put(props.common.objectLocation, summary)
    }


    // Issue a `channel`-only request to this Worker's serve channel; the reply is the serialized TableSummary
    // (SummaryWorker.onQuery ignores the request payload — it always returns its latest snapshot).
    private suspend fun querySummary(clientState: ClientState): TableSummary? {
        val logicRunInfo = clientState.clientLogicState.logicStatus?.active
            ?: return null

        val channelName = JobServeChannelResolver.serveChannelName(
            clientState.graphStructure(), props.common.objectLocation)

        val result = props.restClient.logicRequest(
            logicRunInfo.id,
            logicRunInfo.frame.executionId,
            JobConventions.channelParameter to channelName)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val collection = result.value.get() as? Map<String, Map<String, Any>>
                    ?: return null
                TableSummary.fromCollection(collection)
            }

            is ExecutionFailure ->
                null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        WorkerDisplayDefault::class.react {
            this.attributeEditorManager = props.attributeEditorManager
            this.attributeViewManager = props.attributeViewManager
            this.clientStateGlobal = props.clientStateGlobal
            this.mirroredGraphStore = props.mirroredGraphStore
            this.common = props.common
        }
    }
}
