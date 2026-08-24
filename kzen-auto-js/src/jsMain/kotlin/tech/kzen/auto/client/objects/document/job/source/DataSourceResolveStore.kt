package tech.kzen.auto.client.objects.document.job.source

import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.auto.common.data.model.DataResolveResult
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.location.ObjectLocation


class DataSourceResolveStore(
    private val restClient: ClientRestApi
) {
    internal constructor(
        restClient: ClientRestApi,
        initialStates: Map<ObjectLocation, State>
    ): this(restClient) {
        states.putAll(initialStates)
    }


    internal class Epochs {
        private var nextEpoch = 0
        private val current = mutableMapOf<ObjectLocation, Int>()


        fun issue(source: ObjectLocation): Int {
            val epoch = ++nextEpoch
            current[source] = epoch
            return epoch
        }


        fun invalidate(source: ObjectLocation) {
            if (source in current) {
                current[source] = ++nextEpoch
            }
        }


        fun invalidateAll() {
            for (source in current.keys.toList()) {
                current[source] = ++nextEpoch
            }
        }


        fun isCurrent(source: ObjectLocation, epoch: Int): Boolean {
            return current[source] == epoch
        }
    }


    data class State(
        val resolving: Boolean,
        val result: DataResolveResult?,
        val error: String?
    )


    fun interface Observer {
        fun onDataSourceResolveState(state: State?)
    }


    private val states = mutableMapOf<ObjectLocation, State>()
    private val observers = mutableMapOf<ObjectLocation, MutableSet<Observer>>()
    private val epochs = Epochs()
    private var mounted = false


    fun mount() {
        mounted = true
    }


    fun unmount() {
        mounted = false
        epochs.invalidateAll()
        observers.clear()
    }


    fun observe(source: ObjectLocation, observer: Observer) {
        observers.getOrPut(source, ::mutableSetOf).add(observer)
        observer.onDataSourceResolveState(states[source])
    }


    fun unobserve(source: ObjectLocation, observer: Observer) {
        observers[source]?.remove(observer)
        if (observers[source]?.isEmpty() == true) {
            observers.remove(source)
        }
    }


    fun state(source: ObjectLocation): State? {
        return states[source]
    }


    fun retain(sources: Set<ObjectLocation>) {
        states.keys.filter { it !in sources }.forEach(epochs::invalidate)
        states.keys.retainAll(sources)
    }


    fun resolve(source: ObjectLocation) {
        val epoch = epochs.issue(source)
        publish(source, State(true, states[source]?.result, null))

        async {
            val settled = try {
                when (val execution = restClient.performDetached(
                    DataSourceConventions.dataSourceActionsLocation,
                    DataSourceConventions.sourceParameter to source.asString(),
                    DataSourceConventions.actionParameter to DataSourceConventions.resolveAction
                )) {
                    is ExecutionSuccess -> State(
                        false,
                        DataResolveResult.ofExecutionValue(execution.value),
                        null)

                    is ExecutionFailure -> State(false, null, execution.errorMessage)
                }
            }
            catch (cause: Throwable) {
                State(false, null, cause.message ?: "Data source resolution failed")
            }

            if (mounted && epochs.isCurrent(source, epoch)) {
                publish(source, settled)
            }
        }
    }


    private fun publish(source: ObjectLocation, state: State) {
        states[source] = state
        observers[source]?.toList()?.forEach { it.onDataSourceResolveState(state) }
    }
}
