package tech.kzen.auto.client.objects.document.custom.model

import tech.kzen.auto.client.objects.document.common.raw.DocumentRawHost
import tech.kzen.auto.client.objects.document.common.raw.DocumentRawSnapshot
import tech.kzen.auto.client.objects.document.common.raw.DocumentRawState
import tech.kzen.auto.client.objects.document.common.raw.DocumentRawStore
import tech.kzen.auto.client.objects.document.common.raw.DocumentViewMode
import tech.kzen.auto.client.objects.document.common.raw.unparseDocumentForRawEditor
import tech.kzen.auto.client.objects.document.custom.view.CustomViewStore
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.service.rest.ClientRestTaskRepository
import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.service.store.MirroredGraphStore


class CustomStore(
    val clientStateGlobal: ClientStateGlobal,
    override val mirroredGraphStore: MirroredGraphStore,
    override val notationParser: NotationParser,
    val restClient: ClientRestApi,
    val clientRestTaskRepository: ClientRestTaskRepository
): ClientStateGlobal.Observer, DocumentRawHost {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onCustomState(customState: CustomState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var mounted = false
    private var state: CustomState? = null

    val raw = DocumentRawStore(this)
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
            clientStateGlobal.observe(this)
        }
    }


    fun willUnmount() {
        mounted = false
        state = null
        clientStateGlobal.unobserve(this)
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
                CustomState.initial(
                    documentPath, serverNotation, notationParser, previous?.viewMode ?: DocumentViewMode.View)

            previous.serverNotation == serverNotation ->
                previous

            !previous.editorModified -> {
                val freshEditorValue = notationParser.unparseDocumentForRawEditor(serverNotation)
                previous.withServerNotationAndEditor(serverNotation, freshEditorValue, notationParser)
            }

            else ->
                previous.withServerNotation(serverNotation, notationParser)
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


    fun setViewMode(viewMode: DocumentViewMode) {
        update { it.withViewMode(viewMode) }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun rawSnapshot(): DocumentRawSnapshot? {
        val snapshot = state
            ?: return null
        return DocumentRawSnapshot(snapshot.documentPath, snapshot.raw, snapshot.editorModified)
    }


    override fun updateRaw(updater: (DocumentRawState) -> DocumentRawState) {
        update { it.withRaw(notationParser, updater) }
    }
}
