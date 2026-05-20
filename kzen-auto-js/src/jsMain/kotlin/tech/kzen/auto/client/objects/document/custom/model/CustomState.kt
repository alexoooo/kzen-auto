package tech.kzen.auto.client.objects.document.custom.model

import tech.kzen.auto.client.objects.document.custom.raw.CustomRawState
import tech.kzen.auto.client.objects.document.custom.view.CustomViewState
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation


data class CustomState(
    val documentPath: DocumentPath,
    val serverNotation: DocumentObjectNotation,
    val viewMode: CustomViewMode,
    val raw: CustomRawState,
    val view: CustomViewState,
    val cache: CustomStateCache = CustomStateCache.compute(raw.editorValue, serverNotation)
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun tryFor(clientState: ClientState): Pair<DocumentPath, DocumentObjectNotation>? {
            val documentPath = clientState.navigationRoute.documentPath
                ?: return null

            val documentNotation = clientState
                .graphStructure()
                .graphNotation
                .documents[documentPath]
                ?: return null

            if (!CustomConventions.isCustomDocument(documentNotation)) {
                return null
            }

            return documentPath to documentNotation.objects
        }


        fun initial(
            documentPath: DocumentPath,
            serverNotation: DocumentObjectNotation,
            viewMode: CustomViewMode = CustomViewMode.View
        ): CustomState {
            val editorValue = ClientContext.notationParser.unparseDocument(serverNotation, "")
            val raw = CustomRawState(editorValue = editorValue)
            return CustomState(
                documentPath = documentPath,
                serverNotation = serverNotation,
                viewMode = viewMode,
                raw = raw,
                view = CustomViewState())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    val editorModified: Boolean get() = cache.editorModified


    //-----------------------------------------------------------------------------------------------------------------
    fun withRaw(updater: (CustomRawState) -> CustomRawState): CustomState {
        val updated = updater(raw)
        return if (updated === raw) {
            this
        }
        else if (updated.editorValue != raw.editorValue) {
            copy(raw = updated, cache = CustomStateCache.compute(updated.editorValue, serverNotation))
        }
        else {
            copy(raw = updated)
        }
    }


    fun withView(updater: (CustomViewState) -> CustomViewState): CustomState {
        val updated = updater(view)
        return if (updated === view) {
            this
        }
        else {
            copy(view = updated)
        }
    }


    fun withViewMode(viewMode: CustomViewMode): CustomState {
        return if (viewMode == this.viewMode) {
            this
        }
        else {
            copy(viewMode = viewMode)
        }
    }


    fun withServerNotation(serverNotation: DocumentObjectNotation): CustomState {
        return if (serverNotation == this.serverNotation) {
            this
        }
        else {
            copy(
                serverNotation = serverNotation,
                cache = CustomStateCache.compute(raw.editorValue, serverNotation))
        }
    }


    fun withServerNotationAndEditor(
        serverNotation: DocumentObjectNotation,
        editorValue: String
    ): CustomState {
        if (serverNotation == this.serverNotation && editorValue == raw.editorValue) {
            return this
        }
        return copy(
            serverNotation = serverNotation,
            raw = raw.copy(editorValue = editorValue),
            cache = CustomStateCache.compute(editorValue, serverNotation))
    }
}
