package tech.kzen.auto.client.objects.document.job.source.file

import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataManifest
import tech.kzen.auto.common.data.model.DataResolveResult
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.location.ObjectLocation


class FileResolutionStore private constructor(
    private val resolver: Resolver
) {
    constructor(restClient: ClientRestApi): this(DetachedResolver(restClient))

    internal fun interface Resolver {
        suspend fun resolve(key: Key): ExecutionResult
    }

    private class DetachedResolver(
        private val restClient: ClientRestApi
    ): Resolver {
        override suspend fun resolve(key: Key): ExecutionResult {
            return restClient.performDetached(
                DataSourceConventions.dataSourceActionsLocation,
                DataSourceConventions.sourceParameter to key.source.asString(),
                DataSourceConventions.actionParameter to DataSourceConventions.resolveFileAction,
                DataSourceConventions.locationParameter to key.location.asString(),
                DataSourceConventions.formatParameter to key.format.orEmpty(),
                DataSourceConventions.encodingParameter to key.encoding.orEmpty())
        }
    }

    data class Key(
        val source: ObjectLocation,
        val location: DataLocation,
        val format: String?,
        val encoding: String?,
        val formatGraphIdentity: String
    ) {
        companion object {
            fun of(
                source: ObjectLocation,
                entry: FileSelectionEntry,
                formatGraphIdentity: String
            ): Key = Key(
                source,
                entry.location,
                entry.format?.asString(),
                entry.encoding?.asString(),
                formatGraphIdentity)
        }
    }

    data class Resolution(
        val manifest: DataManifest,
        val part: DataPart,
        val detail: FormatResolutionDetail
    )

    data class State(
        val resolving: Boolean,
        val resolution: Resolution?,
        val error: String?
    )

    fun interface Observer {
        fun onFileResolutionState(key: Key, state: State?)
    }

    internal class Epochs {
        private var nextEpoch = 0
        private val current = mutableMapOf<Key, Int>()

        fun issue(key: Key): Int = (++nextEpoch).also { current[key] = it }

        fun invalidate(key: Key) {
            if (key in current) {
                current[key] = ++nextEpoch
            }
        }

        fun invalidateAll() {
            current.keys.toList().forEach(::invalidate)
        }

        fun isCurrent(key: Key, epoch: Int): Boolean = current[key] == epoch
    }

    companion object {
        internal fun resolution(result: DataResolveResult): Resolution {
            val parts = result.manifest.units.flatMap { it.parts }
            require(parts.size == 1) {
                "File resolution returned ${parts.size} parts; expected one"
            }
            require(result.resolutionDetails.size == 1) {
                "File resolution returned ${result.resolutionDetails.size} details; expected one"
            }
            return Resolution(result.manifest, parts.single(), result.resolutionDetails.single())
        }
    }

    private val states = mutableMapOf<Key, State>()
    private val observers = mutableMapOf<Key, MutableSet<Observer>>()
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

    fun observe(key: Key, observer: Observer) {
        observers.getOrPut(key, ::mutableSetOf).add(observer)
        observer.onFileResolutionState(key, states[key])
    }

    fun unobserve(key: Key, observer: Observer) {
        observers[key]?.remove(observer)
        if (observers[key]?.isEmpty() == true) {
            observers.remove(key)
        }
    }

    fun state(key: Key): State? = states[key]


    fun discard(keys: Set<Key>) {
        keys.forEach(epochs::invalidate)
        states.keys.removeAll(keys)
    }

    fun retainSources(sources: Set<ObjectLocation>) {
        discard(states.keys.filter { it.source !in sources }.toSet())
    }

    fun resolve(key: Key, force: Boolean = false) {
        if (!force && states[key]?.let { it.resolving || it.resolution != null } == true) {
            return
        }

        val epoch = epochs.issue(key)
        publish(key, State(true, states[key]?.resolution, null))
        async {
            val settled = try {
                when (val execution = resolver.resolve(key)) {
                    is ExecutionSuccess -> State(
                        false,
                        resolution(DataResolveResult.ofExecutionValue(execution.value)),
                        null)

                    is ExecutionFailure -> State(false, null, execution.errorMessage)
                }
            }
            catch (cause: Throwable) {
                State(false, null, cause.message ?: "File resolution failed")
            }

            if (mounted && epochs.isCurrent(key, epoch)) {
                publish(key, settled)
            }
        }
    }

    private fun publish(key: Key, state: State) {
        states[key] = state
        observers[key]?.toList()?.forEach { it.onFileResolutionState(key, state) }
    }
}
