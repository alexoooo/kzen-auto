package tech.kzen.auto.client.objects.document.script.model

import tech.kzen.auto.client.objects.document.bridge.BridgeKey


// Owner-provided: ScriptController provides its store into the DocumentBridge in render(); every step
// display in the subtree looks it up. Replaces the former ScriptStoreContext now that all per-document
// state routes through the single DocumentBridge (so each class component spends its one contextType
// slot on DocumentBridgeContext and still reaches the store by key).
object ScriptStoreKey : BridgeKey<ScriptStore>
