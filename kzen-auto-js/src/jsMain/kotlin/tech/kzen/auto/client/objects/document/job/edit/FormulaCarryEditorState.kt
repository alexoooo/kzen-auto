package tech.kzen.auto.client.objects.document.job.edit

import react.State
import tech.kzen.lib.common.model.structure.notation.AttributeNotation


external interface FormulaCarryEditorState: State {
    var notation: AttributeNotation?
    var replacing: Boolean
    var saving: Boolean
    var error: String?
}
