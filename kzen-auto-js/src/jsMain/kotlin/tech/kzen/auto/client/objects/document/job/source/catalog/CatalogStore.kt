package tech.kzen.auto.client.objects.document.job.source.catalog

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.common.data.catalog.CatalogConventions
import tech.kzen.auto.common.data.catalog.CatalogSnapshot
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.location.ObjectLocation

class CatalogStore(private val rest: ClientRestApi, private val location: () -> ObjectLocation) {
    data class State(val catalog: CatalogSnapshot?, val busy: Boolean, val error: String?)
    fun interface Observer { fun onCatalogState(state: State) }
    private val scope = MainScope()
    private var observer: Observer? = null
    private var state = State(null, false, null)

    fun mount(observer: Observer) {
        this.observer = observer
        scope.launch {
            request("list", emptyList())
            request("refresh", emptyList())
            while (isActive) {
                delay(pollMillis)
                if (!state.busy) request("list", emptyList())
            }
        }
    }

    fun unmount() { observer = null; scope.cancel() }
    fun action(action: String, selection: List<String> = emptyList()) {
        if (state.busy) return
        scope.launch { request(action, selection) }
    }

    private suspend fun request(action: String, selection: List<String>) {
        if (action != "list" || state.catalog == null) publish(state.copy(busy = true, error = null))
        try {
            when (val result = rest.performDetached(CatalogConventions.actions,
                CatalogConventions.action to action,
                CatalogConventions.source to location().asString(),
                CatalogConventions.selection to Json.encodeToString(selection))) {
                is ExecutionSuccess -> publish(State(Json.decodeFromString<CatalogSnapshot>(result.value.get() as String), false, null))
                is ExecutionFailure -> publish(State(state.catalog, false, result.errorMessage))
            }
        }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { publish(State(state.catalog, false, e.message ?: "Could not load catalog")) }
    }

    private fun publish(next: State) {
        if (state == next) return
        state = next
        observer?.onCatalogState(next)
    }

    companion object { private const val pollMillis = 1500L }
}
