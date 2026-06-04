package tech.kzen.auto.server.objects.script.model

import tech.kzen.auto.common.objects.document.registry.model.ObjectRegistryScan
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation


data class ScriptDefinitionContext(
    val scriptTree: ScriptTree,
    val scriptValidation: ScriptValidation,
    val objectRegistryScan: ObjectRegistryScan
)