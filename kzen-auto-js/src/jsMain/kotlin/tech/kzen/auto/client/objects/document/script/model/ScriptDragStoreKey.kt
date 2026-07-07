package tech.kzen.auto.client.objects.document.script.model

import tech.kzen.auto.client.objects.document.bridge.BridgeKey
import tech.kzen.auto.client.objects.document.script.display.dependency.ScriptStepDragStore


// Owner-provided: ScriptController provides the shared cross-branch drag source into the DocumentBridge
// in render(); every nested ScriptBranchDisplay looks it up.
object ScriptDragStoreKey : BridgeKey<ScriptStepDragStore>
