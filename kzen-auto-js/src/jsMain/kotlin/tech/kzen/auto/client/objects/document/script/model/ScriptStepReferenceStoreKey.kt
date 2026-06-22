package tech.kzen.auto.client.objects.document.script.model

import tech.kzen.auto.client.objects.document.bridge.BridgeKey
import tech.kzen.auto.client.objects.document.script.display.edit.ScriptStepReferenceStore


// Owner-provided: ScriptController provides the shared step-reference pick session into the DocumentBridge
// in render(); the active KotlinExpressionEditor and every ScriptBranchDisplay look it up. Mirrors
// ScriptDragStoreKey.
object ScriptStepReferenceStoreKey : BridgeKey<ScriptStepReferenceStore>
