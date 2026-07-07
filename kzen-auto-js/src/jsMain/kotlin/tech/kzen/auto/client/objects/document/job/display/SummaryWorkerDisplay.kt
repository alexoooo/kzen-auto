package tech.kzen.auto.client.objects.document.job.display

import emotion.react.css
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
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
import tech.kzen.auto.common.objects.document.report.summary.ColumnSummary
import tech.kzen.auto.common.objects.document.report.summary.NominalValueSummary
import tech.kzen.auto.common.objects.document.report.summary.OpaqueValueSummary
import tech.kzen.auto.common.objects.document.report.summary.StatisticValueSummary
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*
import kotlin.math.round


//---------------------------------------------------------------------------------------------------------------------
external interface SummaryWorkerDisplayProps: WorkerDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper
    var clientStateGlobal: ClientStateGlobal
    var restClient: ClientRestApi
    var mirroredGraphStore: MirroredGraphStore
}


//---------------------------------------------------------------------------------------------------------------------
// Display for a summary-serving Worker (SummaryWorker): the default card, with a compact per-column body rendered
// from the TableSummary the Worker PUSHES to its trace (parsed here from props.common.progress.progressMap's own
// "summary" key) — live during the run and persisted after, so it survives run-end + a browser refresh, exactly
// like PreviewWorkerDisplay's teaser.
// SEPARATELY, a background poll PULLS this Worker's live TableSummary over its duplex `serve` channel and writes its
// OWN entry into the document-scoped JobSummaryStore (reached through the DocumentBridge). The value-set-filter /
// pivot editors observe that store for a column's distinct values; this card is the only producer, so the
// generic controller carries no summary awareness (see CC-17).
// The store is self-constructing (JobSummaryStore.Key.create), so no owner needs to provide it; this card owns only
// its own entry, removing it on unmount (Worker deleted). Kept across a run's end so a filter can still be configured.
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
    @Suppress("ConstPropertyName")
    companion object {
        // How many top nominal values / sample entries a column's compact detail line shows before eliding with "…".
        private const val topNominalValues = 5
        private const val topSampleValues = 5
        private const val maxValueLength = 40
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
        // store; this card writes only its own entry (value-gated inside put). The CARD itself renders from the
        // pushed trace summary (props.common.progress), not from this pull — this pull exists only for the editors.
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
            this.bodyExtra = { it.renderSummary() }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A compact, read-only view of the per-column TableSummary the Worker pushes to its trace: a row-count caption,
    // then one block per column with its detected type and a one-line detail (numeric stats / top nominal values /
    // a small sample). Read from props.common.progress so it's live during the run and persisted after (survives a
    // browser refresh). Empty until the first progress push lands, so the card is header-only until then.
    private fun ChildrenBuilder.renderSummary() {
        val progress = props.common.progress
        val tableSummary = parseSummary(progress?.progressMap?.get("summary"))
        if (tableSummary == null || tableSummary.isEmpty()) {
            return
        }

        val totalRows = progress?.longValue("count")
            ?: tableSummary.columnSummaries.map.values.maxOfOrNull { it.count }
            ?: 0L

        div {
            css {
                marginTop = 0.5.em
                fontSize = 0.8.em
            }

            div {
                css {
                    color = NamedColor.gray
                }
                +"Summary — ${formatCount(totalRows)} row(s) total"
            }

            for ((headerLabel, columnSummary) in tableSummary.columnSummaries.map) {
                if (columnSummary.isEmpty()) {
                    continue
                }
                renderColumn(headerLabel.asString(), columnSummary)
            }
        }
    }


    // This display owns the schema of the progress its Worker publishes: the "summary" key (the pushed per-column
    // TableSummary via common.progress). Kept here, not in the shared JobWorkerProgress, so a 3rd-party Worker's
    // payload never touches general code. Same shape as the duplex serve reply parsed in querySummary.
    @Suppress("UNCHECKED_CAST")
    private fun parseSummary(raw: Any?): TableSummary? {
        val collection = raw as? Map<String, Map<String, Any>>
            ?: return null
        return TableSummary.fromCollection(collection)
    }


    private fun ChildrenBuilder.renderColumn(columnName: String, columnSummary: ColumnSummary) {
        val numeric = columnSummary.numericValueSummary
        val nominal = columnSummary.nominalValueSummary
        val opaque = columnSummary.opaqueValueSummary

        val typeHint = when {
            ! numeric.isEmpty() -> "numeric · count ${formatCount(numeric.count)}"
            ! nominal.isEmpty() -> "nominal · ${formatCount(nominal.histogram.size.toLong())} distinct"
            else -> "sample"
        }

        div {
            key = Key(columnName)
            css {
                marginTop = 0.5.em
                fontFamily = FontFamily.monospace
            }

            div {
                span {
                    css {
                        fontWeight = FontWeight.bold
                    }
                    +columnName
                }
                span {
                    css {
                        color = NamedColor.gray
                        marginLeft = 0.5.em
                    }
                    +typeHint
                }
            }

            div {
                css {
                    marginLeft = 1.em
                }
                +columnDetail(numeric, nominal, opaque)
            }
        }
    }


    // The one-line detail under a column header, chosen by which sub-summary is populated (mirrors the
    // numeric / nominal / opaque precedence in the Report's FilterItemController.renderDetail).
    private fun columnDetail(
        numeric: StatisticValueSummary,
        nominal: NominalValueSummary,
        opaque: OpaqueValueSummary
    ): String {
        if (! numeric.isEmpty()) {
            val mean = numeric.sum / numeric.count
            return "min ${formatNumber(numeric.min)} · " +
                    "max ${formatNumber(numeric.max)} · " +
                    "mean ${formatNumber(mean)}"
        }

        if (! nominal.isEmpty()) {
            val top = nominal.histogram.entries
                .sortedByDescending { it.value }
                .take(topNominalValues)
            val rendered = top.joinToString(" · ") { "${abbreviate(it.key)} ${formatCount(it.value)}" }
            return if (nominal.histogram.size > top.size) "$rendered …" else rendered
        }

        if (! opaque.isEmpty()) {
            val sample = opaque.sample.take(topSampleValues)
            val rendered = sample.joinToString(" · ") { abbreviate(it) }
            return if (opaque.sample.size > sample.size) "$rendered …" else rendered
        }

        return ""
    }


    // Thousands-separated integer (mirrors the Report's FilterItemController.formatCount).
    private fun formatCount(count: Long): String {
        return count.toString()
            .replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")
    }


    // A compact decimal: round to 3 places, drop a trailing ".0" (JS-safe — the JVM ColumnValueUtils formatter isn't).
    private fun formatNumber(value: Double): String {
        if (! value.isFinite()) {
            return value.toString()
        }
        val rounded = round(value * 1000.0) / 1000.0
        val asLong = rounded.toLong()
        return if (rounded == asLong.toDouble()) {
            formatCount(asLong)
        }
        else {
            rounded.toString()
        }
    }


    private fun abbreviate(value: String): String {
        return if (value.length > maxValueLength) {
            value.substring(0, maxValueLength) + "…"
        }
        else {
            value
        }
    }
}
