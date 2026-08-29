package tech.kzen.auto.server.objects.script.model

import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.model.structure.notation.GraphNotation


data class ScriptDefinitionContext(
    val scriptTree: ScriptTree,
    val scriptValidation: ScriptValidation,
    // The Script's declared result signature; a ResultStep type-checks its expression against `main`.
    val resultSignature: BindingSchema,
    // Full graph notation — lets a RunStep resolve the declared result signature of its linked sub-Script.
    val graphNotation: GraphNotation
)
