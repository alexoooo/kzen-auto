package tech.kzen.auto.client.objects.document.script.step.control

import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayBaseProps
import tech.kzen.auto.client.objects.document.script.display.StepDisplayManager


// Props of a branch-bearing control step display (If / ForEach / DoWhile): the observer services plus an
// editor manager for its own condition/items attribute and the recursive step-display machinery its branches
// render through. The three are structurally identical, so they share one interface.
external interface BranchStepDisplayProps: ScriptStepDisplayBaseProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var stepDisplayManager: StepDisplayManager.Wrapper
    var scriptCommander: ScriptCommander
}
