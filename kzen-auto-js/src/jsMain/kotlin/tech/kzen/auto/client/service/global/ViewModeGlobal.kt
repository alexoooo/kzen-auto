package tech.kzen.auto.client.service.global


// Command channel from the ribbon (header slot) to the active document body (a sibling React subtree)
// for switching the document's stage view (e.g. structured View <-> Raw YAML). Mirrors InsertionGlobal:
// the ribbon publishes the selected view id (declared per RibbonGroup in notation), the active document
// subscribes and reacts. An empty id means the default (structured) view.
class ViewModeGlobal {
    //-----------------------------------------------------------------------------------------------------------------
    interface Subscriber {
        fun onViewModeChanged(viewMode: String)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val subscribers = mutableListOf<Subscriber>()


    //-----------------------------------------------------------------------------------------------------------------
    fun subscribe(subscriber: Subscriber) {
        subscribers.add(subscriber)
    }


    fun unsubscribe(subscriber: Subscriber) {
        subscribers.remove(subscriber)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // NB: no retained 'current' state and always notifies — the channel is a pure command stream, so a
    //     freshly-mounted document never inherits a stale mode and re-selecting the active tab still fires.
    fun set(viewMode: String) {
        for (subscriber in subscribers.toList()) {
            subscriber.onViewModeChanged(viewMode)
        }
    }
}
