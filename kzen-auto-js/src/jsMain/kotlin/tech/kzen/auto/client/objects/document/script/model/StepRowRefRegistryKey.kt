package tech.kzen.auto.client.objects.document.script.model

import react.Component
import tech.kzen.auto.client.objects.document.bridge.BridgeKey
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.script.display.dependency.StepRowRefRegistry
import tech.kzen.auto.client.wrap.contextValue


// Owner-provided: ScriptController provides the shared step-row rect registry into the DocumentBridge in
// render(); the dependency overlay, the move-to arrow, the branch display's drag insertion and the signature
// editor's parameter rows all reach the same instance through it.
//
// NB: owner-provided rather than a self-constructing channel ON PURPOSE. The bridge is recreated on every
//     document-path change, while ScriptController and its overlay/arrow children persist across a
//     same-archetype switch — a channel-created registry would be a fresh instance in the fresh bridge,
//     leaving those not-remounted children subscribed to the orphaned old one (silently missing polylines
//     and arrow until an unrelated remeasure). A controller-field instance is re-provided into each fresh
//     bridge, so mount-time subscriptions stay valid.
object StepRowRefRegistryKey : BridgeKey<StepRowRefRegistry>


// Shared by the four consumers, each of which spends its single contextType slot on DocumentBridgeContext
// (see scriptDependencyAnalysis for the same shape). Null only where no bridge is upstream — which doesn't
// happen under ProjectController; consumers then simply skip the row rather than failing.
fun Component<*, *>.stepRowRefRegistry(): StepRowRefRegistry? =
    contextValue<DocumentBridge?>()?.lookup(StepRowRefRegistryKey)
