package tech.kzen.auto.client.objects.document.script.model

import tech.kzen.auto.client.objects.document.common.raw.DocumentRawModified
import tech.kzen.auto.client.objects.document.common.raw.DocumentRawState
import tech.kzen.auto.client.objects.document.common.raw.DocumentViewMode
import tech.kzen.auto.client.objects.document.script.progress.ScriptProgressState
import tech.kzen.auto.client.objects.document.script.valid.ScriptValidationState
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.parse.NotationParser


data class ScriptState(
    val mainLocation: ObjectLocation,
    val documentNotation: DocumentNotation,
    val scriptTree: ScriptTree,

    // Raw (YAML) view of the document's objects — edited/saved via DocumentRawStore. editorModified is
    // cached (computed against documentNotation.objects) rather than recomputed per read, mirroring Custom.
    val raw: DocumentRawState,
    val editorModified: Boolean,

    val viewMode: DocumentViewMode = DocumentViewMode.View,
    val progress: ScriptProgressState = ScriptProgressState(),
    val validationState: ScriptValidationState = ScriptValidationState(),
    val steps: Map<ObjectLocation, ScriptStepState> = mapOf(),

    val globalError: String? = null
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun tryMainLocation(clientState: ClientState): ObjectLocation? {
            val documentPath = clientState
                .navigationRoute
                .documentPath
                ?: return null

            val documentNotation = clientState
                .graphStructure()
                .graphNotation
                .documents[documentPath]
                ?: return null

            if (!ScriptConventions.isScript(documentNotation)) {
                return null
            }

            return documentPath.toMainObjectLocation()
        }


        fun initial(
            mainLocation: ObjectLocation,
            documentNotation: DocumentNotation,
            scriptTree: ScriptTree,
            notationParser: NotationParser,
            viewMode: DocumentViewMode = DocumentViewMode.View
        ): ScriptState {
            val objects = documentNotation.objects
            val editorValue = notationParser.unparseDocument(objects, "")
            return ScriptState(
                mainLocation = mainLocation,
                documentNotation = documentNotation,
                scriptTree = scriptTree,
                raw = DocumentRawState(editorValue = editorValue),
                editorModified = DocumentRawModified.compute(editorValue, objects, notationParser),
                viewMode = viewMode)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withGlobalError(globalError: String): ScriptState {
        return copy(
            globalError = globalError)
    }


    fun withProgressSuccess(updater: (ScriptProgressState) -> ScriptProgressState): ScriptState {
        return copy(
            progress = updater(progress),
            globalError = null)
    }


    fun withValidation(updater: (ScriptValidationState) -> ScriptValidationState): ScriptState {
        return copy(
            validationState = updater(validationState),
            globalError = null)
    }


    fun isStepExpanded(objectLocation: ObjectLocation): Boolean {
        return steps[objectLocation]?.expanded ?: false
    }


    // The sub-script step whose frame this RunStep's right-of-step preview should show (null = the
    // latest/representative frame). Driven by hovering a strip thumbnail; see ScriptStepStore.
    fun hoveredScreenshot(objectLocation: ObjectLocation): ObjectLocation? {
        return steps[objectLocation]?.hoveredScreenshot
    }


    // Thin plumbing (mirrors withValidation): ScriptStepStore owns the step-state management and
    // passes the new map in. No globalError reset — step UI is unrelated to errors.
    fun withSteps(
        updater: (Map<ObjectLocation, ScriptStepState>) -> Map<ObjectLocation, ScriptStepState>
    ): ScriptState {
        return copy(steps = updater(steps))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withViewMode(viewMode: DocumentViewMode): ScriptState {
        return if (viewMode == this.viewMode) {
            this
        }
        else {
            copy(viewMode = viewMode)
        }
    }


    fun withRaw(notationParser: NotationParser, updater: (DocumentRawState) -> DocumentRawState): ScriptState {
        val updated = updater(raw)
        return if (updated === raw) {
            this
        }
        else if (updated.editorValue != raw.editorValue) {
            copy(
                raw = updated,
                editorModified = DocumentRawModified.compute(
                    updated.editorValue, documentNotation.objects, notationParser))
        }
        else {
            copy(raw = updated)
        }
    }


    // Apply a fresh server notation (and its derived scriptTree). When the editor has no unsaved
    // changes, follow the server by re-seeding editorValue; otherwise keep the user's edits and just
    // recompute the modified flag against the new objects. Mirrors CustomStore.onClientState's branches.
    fun withDocumentNotation(
        documentNotation: DocumentNotation,
        scriptTree: ScriptTree,
        notationParser: NotationParser
    ): ScriptState {
        val objects = documentNotation.objects
        // NB: preserve the previous scriptTree reference when structurally equal, so downstream
        //     RPureComponents keyed on it don't re-render on attribute-only edits.
        val nextTree = if (scriptTree == this.scriptTree) this.scriptTree else scriptTree
        return if (!editorModified) {
            val freshEditor = notationParser.unparseDocument(objects, "")
            copy(
                documentNotation = documentNotation,
                scriptTree = nextTree,
                raw = raw.copy(editorValue = freshEditor),
                editorModified = DocumentRawModified.compute(freshEditor, objects, notationParser))
        }
        else {
            copy(
                documentNotation = documentNotation,
                scriptTree = nextTree,
                editorModified = DocumentRawModified.compute(raw.editorValue, objects, notationParser))
        }
    }
}