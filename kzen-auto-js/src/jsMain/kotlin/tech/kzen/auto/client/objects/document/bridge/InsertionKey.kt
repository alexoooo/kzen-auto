package tech.kzen.auto.client.objects.document.bridge

import tech.kzen.auto.client.service.global.InsertionGlobal


// Framework-level generic ribbon→stage channel: the ribbon (header) publishes the selected insertion
// action, the active document body subscribes and inserts it on click. Self-constructing — lazily
// created on first touch. Replaces the former app-global InsertionGlobal singleton; any document type
// (including downstream) may participate by using this same key.
object InsertionKey : BridgeKey<InsertionGlobal> {
    override fun create() = InsertionGlobal()
}
