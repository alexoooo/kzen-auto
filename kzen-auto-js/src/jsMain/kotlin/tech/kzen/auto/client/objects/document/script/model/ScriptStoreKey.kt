package tech.kzen.auto.client.objects.document.script.model

import tech.kzen.auto.client.objects.document.bridge.BridgeKey


// Owner-provided: ScriptController provides its store into the DocumentBridge in render(); every step
// display in the subtree looks it up. Keyed lookup through the one DocumentBridge lets a class
// component reach the store despite its single contextType slot (DocumentBridgeContext).
object ScriptStoreKey : BridgeKey<ScriptStore>
