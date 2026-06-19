package tech.kzen.auto.client.objects.document.bridge

import tech.kzen.auto.client.service.global.ViewModeGlobal


// Framework-level generic ribbon→stage channel: the ribbon (header) publishes the selected view id
// (e.g. "Raw"), the active document body subscribes and switches its stage. Self-constructing — lazily
// created on first touch. Replaces the former app-global ViewModeGlobal singleton; any document type
// (including downstream) may subscribe by using this same key.
object ViewModeKey : BridgeKey<ViewModeGlobal> {
    override fun create() = ViewModeGlobal()
}
