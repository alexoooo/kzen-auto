package tech.kzen.auto.client.objects.document.job

import tech.kzen.auto.client.objects.document.bridge.BridgeKey
import tech.kzen.auto.common.objects.document.report.summary.TableSummary
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * Per-document broadcast of the live [TableSummary] each SummaryWorker in the running Job serves, keyed by that
 * Worker's [ObjectLocation]. [JobController] — the single owner of the run-scoped duplex serve query — pulls
 * these each poll while the run is active and pushes them here (value-gated); the value-set-filter / pivot
 * attribute editors OBSERVE this to source a column's distinct values from the nearest upstream SummaryWorker.
 *
 * Those editors render deep under the generic AttributeEditorManager, whose dispatch carries only objectLocation
 * + attributeName — so a live summary cannot reach them through props, and giving each editor its own restClient
 * would duplicate the controller's channel-name resolution (and let the two drift). Instead JobController
 * `provide`s this store into the per-document DocumentBridge (owner-constructed, so it is document-scoped, not a
 * process-global) and the editors look it up + observe it — the same bridge seam ScriptStore uses to reach the
 * step-display subtree.
 */
class JobSummaryStore {
    //-----------------------------------------------------------------------------------------------------------------
    // Owner-provided bridge key (no factory): JobController constructs the store and calls DocumentBridge.provide.
    object Key: BridgeKey<JobSummaryStore>


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
    // Replace + notify only on an actual change, so an unchanged poll doesn't re-render observing editors
    // (TableSummary / ColumnSummary / NominalValueSummary are data classes → structural compare). Notify over a
    // copy of the observer list so an observer that unobserves in its callback doesn't mutate it mid-iteration.
    fun update(next: Map<ObjectLocation, TableSummary>) {
        if (next == summaries) {
            return
        }
        summaries = next
        for (observer in observers.toList()) {
            observer.onJobSummaries(next)
        }
    }
}
