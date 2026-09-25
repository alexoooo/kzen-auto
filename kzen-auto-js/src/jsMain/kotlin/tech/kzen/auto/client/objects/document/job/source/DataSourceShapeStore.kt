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


/**
 * Client-only results of explicit, user-triggered inspection of single parts, for authoring a file's explicit format
 * (locking its observed columns). Nothing here types a Job: a Worker's type before Run is the server's validation
 * (docs/plans/2026-09-24_values-metadata-and-design-time-types.md, R5), and no notation walk calls this store.
 */
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
        val parts: Map<DataPart, PartState>
    )


    fun interface GlobalObserver {
        fun onDataSourceShapesChanged()
    }


    private val epochs = Epochs()
    private val states = mutableMapOf<Key, State>()
    private val globalObservers = mutableSetOf<GlobalObserver>()
    private var mounted = false


    fun mount() {
        mounted = true
    }


    fun unmount() {
        mounted = false
        invalidateAll()
        globalObservers.clear()
    }


    /** Exact preview-part lookup: equality includes the resolved read spec and expected content fingerprint. */
    fun partState(source: ObjectLocation, part: DataPart): PartState? =
        states.entries.toList().asReversed().firstNotNullOfOrNull { (key, state) ->
            if (key.source == source) state.parts[part] else null
        }


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
        publish(key, State(uniqueParts.associateWith { PartState(true, null, null) }))

        async {
            var current = states[key] ?: State(emptyMap())
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


    private fun stateWith(state: State, part: DataPart, partState: PartState): State =
        State(state.parts + (part to partState))


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
        globalObservers.toList().forEach { it.onDataSourceShapesChanged() }
    }
}
