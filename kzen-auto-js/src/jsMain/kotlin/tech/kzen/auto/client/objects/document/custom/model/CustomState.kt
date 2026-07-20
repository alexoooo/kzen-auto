package tech.kzen.auto.client.objects.document.custom.model

import tech.kzen.auto.client.objects.document.common.raw.DocumentRawModified
import tech.kzen.auto.client.objects.document.common.raw.DocumentRawState
import tech.kzen.auto.client.objects.document.common.raw.DocumentViewMode
import tech.kzen.auto.client.objects.document.common.raw.unparseDocumentForRawEditor
import tech.kzen.auto.client.objects.document.custom.view.CustomViewState
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.auto.common.objects.document.custom.model.CustomViewModel
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.service.parse.NotationParser


data class CustomState(
    val documentPath: DocumentPath,
    val serverNotation: DocumentObjectNotation,
    val viewMode: DocumentViewMode,
    val raw: DocumentRawState,
    val view: CustomViewState,
    val editorModified: Boolean,
    // Derived from the notation by CustomStore per ClientStateGlobal publish (not per render — the prototype list
    // is a full-graph scan). Null until the first publish carrying a graph structure.
    val viewModel: CustomViewModel? = null
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
            notationParser: NotationParser,
            viewMode: DocumentViewMode = DocumentViewMode.View
        ): CustomState {
            val editorValue = notationParser.unparseDocumentForRawEditor(serverNotation)
            val raw = DocumentRawState(editorValue = editorValue)
            return CustomState(
                documentPath = documentPath,
                serverNotation = serverNotation,
                viewMode = viewMode,
                raw = raw,
                view = CustomViewState(),
                editorModified = DocumentRawModified.compute(editorValue, serverNotation, notationParser))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withRaw(notationParser: NotationParser, updater: (DocumentRawState) -> DocumentRawState): CustomState {
        val updated = updater(raw)
        return if (updated === raw) {
            this
        }
        else if (updated.editorValue != raw.editorValue) {
            copy(
                raw = updated,
                editorModified = DocumentRawModified.compute(updated.editorValue, serverNotation, notationParser))
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


    fun withViewModel(viewModel: CustomViewModel?): CustomState {
        // Reference comparison on purpose: the Builder hands back the previous instance when nothing changed.
        return if (viewModel === this.viewModel) {
            this
        }
        else {
            copy(viewModel = viewModel)
        }
    }


    fun withViewMode(viewMode: DocumentViewMode): CustomState {
        return if (viewMode == this.viewMode) {
            this
        }
        else {
            copy(viewMode = viewMode)
        }
    }


    fun withServerNotation(
        serverNotation: DocumentObjectNotation,
        notationParser: NotationParser
    ): CustomState {
        return if (serverNotation == this.serverNotation) {
            this
        }
        else {
            copy(
                serverNotation = serverNotation,
                editorModified = DocumentRawModified.compute(raw.editorValue, serverNotation, notationParser))
        }
    }


    fun withServerNotationAndEditor(
        serverNotation: DocumentObjectNotation,
        editorValue: String,
        notationParser: NotationParser
    ): CustomState {
        if (serverNotation == this.serverNotation && editorValue == raw.editorValue) {
            return this
        }
        return copy(
            serverNotation = serverNotation,
            raw = raw.copy(editorValue = editorValue),
            editorModified = DocumentRawModified.compute(editorValue, serverNotation, notationParser))
    }
}
