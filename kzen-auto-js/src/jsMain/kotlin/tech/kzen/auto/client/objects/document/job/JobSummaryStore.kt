package tech.kzen.auto.client.objects.document.job

import tech.kzen.auto.client.objects.document.bridge.BridgeKey
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * Per-document broadcast of the live [TableSummary] each SummaryWorker in the running Job serves, keyed by that
 * Worker's [ObjectLocation]. Each SummaryWorker's own card ([tech.kzen.auto.client.objects.document.job.display.SummaryWorkerDisplay])
 * pulls its TableSummary over the Worker's duplex serve channel while the run is active and writes its own entry
 * here (value-gated); the value-set-filter / pivot attribute editors OBSERVE this to source a column's distinct
 * values from the nearest upstream SummaryWorker.
 *
 * Those editors render deep under the generic AttributeEditorManager, whose dispatch carries only objectLocation
 * + attributeName — so a live summary cannot reach them through props. Instead this store is a self-constructing
 * [DocumentBridge] channel (see [Key]): whichever card / editor touches the key first lazily creates the one
 * document-scoped instance, and everyone else gets the same one. No component OWNS it — each SummaryWorker card
 * writes only its own entry (removing it on unmount), and the editors only read — so the generic JobController
 * carries no summary awareness (see CC-17). Document-scoping is preserved because the bridge itself is created
 * once per mounted document by `ProjectController`.
 */
class JobSummaryStore {
    //-----------------------------------------------------------------------------------------------------------------
    // Self-constructing bridge key: dependency-free, so the bridge lazily builds one instance per document on first
    // touch (see DocumentBridge.channel) — no owner needs to provide it.
    object Key: BridgeKey<JobSummaryStore> {
        override fun create(): JobSummaryStore = JobSummaryStore()
    }


    interface Observer {
        fun onJobSummaries(summaries: Map<ObjectLocation, TableSummary>)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableListOf<Observer>()
    private var summaries: Map<ObjectLocation, TableSummary> = mapOf()


    //-----------------------------------------------------------------------------------------------------------------
    fun observe(observer: Observer) {
        observers.add(observer)
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    fun current(): Map<ObjectLocation, TableSummary> {
        return summaries
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Set one Worker's summary + notify only on an actual change, so an unchanged poll doesn't re-render observing
    // editors (TableSummary / ColumnSummary / NominalValueSummary are data classes → structural compare). One entry
    // per SummaryWorker card, so N cards each own their own key without clobbering the others.
    fun put(location: ObjectLocation, summary: TableSummary) {
        if (summaries[location] == summary) {
            return
        }
        summaries = summaries + (location to summary)
        notifyObservers()
    }


    // Drop one Worker's summary (its card unmounted — the Worker was deleted / the document switched).
    fun remove(location: ObjectLocation) {
        if (location !in summaries) {
            return
        }
        summaries = summaries - location
        notifyObservers()
    }


    // Notify over a copy of the observer list so an observer that unobserves in its callback doesn't mutate it
    // mid-iteration.
    private fun notifyObservers() {
        val snapshot = summaries
        for (observer in observers.toList()) {
            observer.onJobSummaries(snapshot)
        }
    }
}
