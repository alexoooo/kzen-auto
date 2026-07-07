package tech.kzen.auto.client.objects.document.bridge

import tech.kzen.auto.client.service.global.ViewModeGlobal


// Generic ribbon→stage channel: the ribbon publishes the selected view id (e.g. "Raw"), the active
// document body subscribes and switches its stage. Self-constructing (lazily created on first
// touch); any document type (including downstream) may subscribe.
object ViewModeKey : BridgeKey<ViewModeGlobal> {
    override fun create() = ViewModeGlobal()
}
