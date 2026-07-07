package tech.kzen.auto.client.objects.document.custom.model

import tech.kzen.auto.client.objects.document.bridge.BridgeKey


// Owner-provided: CustomController provides its store into the DocumentBridge at the top of render()
// (before its early return), so the sibling CustomHeader in the header slot resolves it in its
// componentDidMount — which runs after that render.
object CustomStoreKey : BridgeKey<CustomStore>
