package tech.kzen.auto.client.objects.document.custom.model

import tech.kzen.auto.client.objects.document.custom.raw.CustomRawStore
import tech.kzen.auto.client.objects.document.custom.view.CustomViewStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async


class CustomStore: ClientStateGlobal.Observer {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onCustomState(customState: CustomState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var mounted = false
    private var state: CustomState? = null

    val raw = CustomRawStore(this)
    val view = CustomViewStore(this)


    //-----------------------------------------------------------------------------------------------------------------
    fun observe(observer: Observer) {
        observers.add(observer)
        state?.let { observer.onCustomState(it) }
    }


    fun unobserve(observer: Observer) {
        val removed = observers.remove(observer)
        check(removed) { "Not found: $observer" }
    }


    private fun publish(nextState: CustomState) {
        for (observer in observers.toList()) {
            observer.onCustomState(nextState)
        }
    }


    fun didMount() {
        mounted = true
        async {
            ClientContext.clientStateGlobal.observe(this)
        }
    }


    fun willUnmount() {
        mounted = false
        state = null
        ClientContext.clientStateGlobal.unobserve(this)
        observers.clear()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        if (!mounted) {
            return
        }

        val resolved = CustomState.tryFor(clientState)
            ?: return
        val (documentPath, serverNotation) = resolved

        val previous = state
        val nextState = when {
            previous == null || previous.documentPath != documentPath ->
                CustomState.initial(documentPath, serverNotation, previous?.viewMode ?: CustomViewMode.View)

            previous.serverNotation == serverNotation ->
                previous

            !previous.editorModified -> {
                val freshEditorValue = ClientContext.notationParser.unparseDocument(serverNotation, "")
                previous.withServerNotationAndEditor(serverNotation, freshEditorValue)
            }

            else ->
                previous.withServerNotation(serverNotation)
        }

        updateIfChanged(nextState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun state(): CustomState {
        return state
            ?: error("Get state before initialized")
    }


    fun stateOrNull(): CustomState? {
        return state
    }


    fun update(updater: (CustomState) -> CustomState) {
        val initialized = state
            ?: return
        updateIfChanged(updater(initialized))
    }


    fun updateIfChanged(nextState: CustomState) {
        if (state == nextState) {
            return
        }
        state = nextState
        publish(nextState)
    }


    fun setViewMode(viewMode: CustomViewMode) {
        update { it.withViewMode(viewMode) }
    }
}
