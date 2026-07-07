package tech.kzen.auto.client.objects.document.bridge

import tech.kzen.auto.client.service.global.InsertionGlobal


// Generic ribbon→stage channel: the ribbon publishes the selected insertion action, the active
// document body subscribes and inserts it on click. Self-constructing (lazily created on first
// touch); any document type (including downstream) may participate.
object InsertionKey : BridgeKey<InsertionGlobal> {
    override fun create() = InsertionGlobal()
}
