package tech.kzen.auto.client.objects.document.job.source

import kotlinx.serialization.encodeToString
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.util.clientJson
import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.auto.common.data.model.DataManifest
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.data.shape.DataShapeResult
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.util.digest.Digest


/** Client-only results of explicit, user-triggered bounded part inspection. No notation walk calls this store. */
class DataSourceShapeStore(
    private val restClient: ClientRestApi
) {
    internal constructor(
        restClient: ClientRestApi,
        initialStates: Map<Key, State>
    ): this(restClient) {
        states.putAll(initialStates)
    }


    internal class Epochs {
        private var nextEpoch = 0
        private val current = mutableMapOf<Key, Int>()

        fun issue(key: Key): Int = (++nextEpoch).also { current[key] = it }
        fun invalidate(key: Key) {
            if (key in current) current[key] = ++nextEpoch
        }
        fun invalidateAll() = current.keys.toList().forEach(::invalidate)
        fun isCurrent(key: Key, epoch: Int): Boolean = current[key] == epoch
    }


    companion object {
        internal fun aggregate(parts: Collection<PartState>): DataShapeResult? {
            val settled = parts.filterNot { it.inspecting }
            if (settled.size != parts.size || settled.any { it.error != null }) {
                return null
            }
            val results = settled.mapNotNull { it.result }
            if (results.size != settled.size || results.any { it == DataShapeResult.Unavailable }) {
                return DataShapeResult.Unavailable
            }
            val shapes = results.map { (it as DataShapeResult.Observed).shape }
            val first = shapes.firstOrNull() ?: return DataShapeResult.Unavailable
            return if (shapes.all { it == first }) {
                DataShapeResult.Observed(first)
            }
            else {
                // A source-wide summary must not erase incompatible field types into a legacy text header.
                // Per-lane strict/superset projection combines the complete contracts with its configured policy.
                DataShapeResult.Unavailable
            }
        }
    }


    data class Key(
        val source: ObjectLocation,
        val manifestDigest: Digest
    ) {
        companion object {
            fun of(source: ObjectLocation, manifest: DataManifest): Key =
                Key(source, Digest.build { addDigestible(manifest) })
        }
    }


    data class PartState(
        val inspecting: Boolean,
        val result: DataShapeResult?,
        val error: String?
    ) {
        val shape: DataShape?
            get() = (result as? DataShapeResult.Observed)?.shape
    }


    data class State(
        val parts: Map<DataPart, PartState>,
        val aggregate: DataShapeResult?
    )


    fun interface Observer {
        fun onDataSourceShapeState(state: State?)
    }


    fun interface GlobalObserver {
        fun onDataSourceShapesChanged()
    }


    private val epochs = Epochs()
    private val states = mutableMapOf<Key, State>()
    private val observers = mutableMapOf<Key, MutableSet<Observer>>()
    private val globalObservers = mutableSetOf<GlobalObserver>()
    private var mounted = false


    fun mount() {
        mounted = true
    }


    fun unmount() {
        mounted = false
        invalidateAll()
        observers.clear()
        globalObservers.clear()
    }


    fun observe(key: Key, observer: Observer) {
        observers.getOrPut(key, ::mutableSetOf).add(observer)
        observer.onDataSourceShapeState(states[key])
    }


    fun unobserve(key: Key, observer: Observer) {
        observers[key]?.remove(observer)
        if (observers[key]?.isEmpty() == true) {
            observers.remove(key)
        }
    }


    fun state(key: Key): State? = states[key]


    fun observeAll(observer: GlobalObserver) {
        globalObservers.add(observer)
    }


    fun unobserveAll(observer: GlobalObserver) {
        globalObservers.remove(observer)
    }


    fun retain(sources: Set<ObjectLocation>) {
        val stale = states.keys.filter { it.source !in sources }
        stale.forEach(::invalidate)
        states.keys.removeAll(stale.toSet())
    }


    fun inspect(source: ObjectLocation, manifest: DataManifest) {
        val key = Key.of(source, manifest)
        val epoch = issue(key)
        val uniqueParts = manifest.units.flatMap { it.parts }.distinct()
        publish(key, State(uniqueParts.associateWith { PartState(true, null, null) }, null))

        async {
            var current = states[key] ?: State(emptyMap(), null)
            for (part in uniqueParts) {
                val partState = inspectPart(source, part)
                if (!mounted || !epochs.isCurrent(key, epoch)) {
                    return@async
                }
                current = stateWith(current, part, partState)
                publish(key, current)
            }
        }
    }


    private suspend fun inspectPart(source: ObjectLocation, part: DataPart): PartState {
        return try {
            val body = clientJson.encodeToString(DataPart.serializer(), part).encodeToByteArray()
            when (val result = restClient.performDetached(
                DataSourceConventions.dataSourceActionsLocation,
                body,
                DataSourceConventions.sourceParameter to source.asString(),
                DataSourceConventions.actionParameter to DataSourceConventions.shapeAction
            )) {
                is ExecutionSuccess -> PartState(
                    false,
                    DataShapeResult.Observed(DataShape.ofExecutionValue(result.value)),
                    null)
                is ExecutionFailure -> PartState(false, null, result.errorMessage)
            }
        }
        catch (cause: Throwable) {
            PartState(false, null, cause.message ?: "Data shape inspection failed")
        }
    }


    private fun stateWith(state: State, part: DataPart, partState: PartState): State {
        val nextParts = state.parts + (part to partState)
        return State(nextParts, aggregate(nextParts.values))
    }


    private fun issue(key: Key): Int {
        return epochs.issue(key)
    }


    private fun invalidate(key: Key) {
        epochs.invalidate(key)
    }


    private fun invalidateAll() {
        epochs.invalidateAll()
    }


    private fun publish(key: Key, state: State) {
        states[key] = state
        observers[key]?.toList()?.forEach { it.onDataSourceShapeState(state) }
        globalObservers.toList().forEach { it.onDataSourceShapesChanged() }
    }
}
