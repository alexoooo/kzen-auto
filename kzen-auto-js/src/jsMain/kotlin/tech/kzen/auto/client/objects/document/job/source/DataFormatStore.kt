package tech.kzen.auto.client.objects.document.job.source

import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.auto.common.data.format.FileFormatCatalog
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess


/**
 * The Format / Encoding option lists, fetched once per mounted document and shared by every card that offers them.
 *
 * Unlike its sibling stores this is keyed by nothing: the answer describes the server's installed definitions, not
 * any one source, so a Job with ten File Workers still asks once. The fetch is lazy — a document whose cards never
 * expose a format select never issues it — and a failure leaves the selects offering what the notation already
 * holds rather than blocking the edit.
 */
class DataFormatStore(
    private val restClient: ClientRestApi
) {
    data class State(
        val loading: Boolean,
        val catalog: FileFormatCatalog?,
        val error: String?
    )


    fun interface Observer {
        fun onDataFormatState(state: State)
    }


    private var state = State(false, null, null)
    private val observers = mutableSetOf<Observer>()
    private var mounted = false


    fun mount() {
        mounted = true
    }


    fun unmount() {
        mounted = false
        observers.clear()
    }


    /** Observing is also what asks for the catalogue: nothing else knows that someone is about to need it. */
    fun observe(observer: Observer) {
        observers.add(observer)
        observer.onDataFormatState(state)
        load()
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    private fun load() {
        if (state.loading || state.catalog != null) {
            return
        }

        publish(State(true, null, null))

        async {
            val settled = try {
                when (val execution = restClient.performDetached(
                    DataSourceConventions.dataSourceActionsLocation,
                    DataSourceConventions.actionParameter to DataSourceConventions.fileFormatsAction
                )) {
                    is ExecutionSuccess -> {
                        @Suppress("UNCHECKED_CAST")
                        val collection = execution.value.get() as Map<String, Any?>
                        State(false, FileFormatCatalog.ofCollection(collection), null)
                    }

                    is ExecutionFailure ->
                        State(false, null, execution.errorMessage)
                }
            }
            catch (cause: Throwable) {
                State(false, null, cause.message ?: "Data format listing failed")
            }

            if (mounted) {
                publish(settled)
            }
        }
    }


    private fun publish(next: State) {
        state = next
        observers.toList().forEach { it.onDataFormatState(next) }
    }
}
