package tech.kzen.auto.client.objects.document.bridge


/**
 * Identifies one entry in a [DocumentBridge]. Keys are singletons (`object`s) so map identity is
 * stable and unique per channel/store. The generic parameter keeps lookups type-safe.
 *
 * A key for a dependency-free pub/sub channel overrides [create] so the bridge can lazily construct
 * it on first touch (see [DocumentBridge.channel]). A key for an owner-provided store leaves [create]
 * as null and the owning controller calls [DocumentBridge.provide] (see [DocumentBridge.lookup]).
 */
interface BridgeKey<T> {
    fun create(): T? = null
}


/**
 * Per-document communication hub between a document's header (ribbon) and stage (body), which are
 * sibling React components with no shared parent state. Created once per mounted document by
 * `ProjectController` and handed to both subtrees through a single React context
 * ([DocumentBridgeContext]); both sides reach channels/stores by [BridgeKey].
 *
 * This replaces the former per-document `*Global` singletons (InsertionGlobal, ViewModeGlobal,
 * CustomGlobal) and the per-subtree `ScriptStoreContext` / `ScriptStepDragStoreContext` — folding
 * them into one keyed container so each class component spends only its single `contextType` slot on
 * this bridge yet reaches everything by key. It is open for extension: a downstream document type
 * defines its own `BridgeKey` without touching framework code.
 */
class DocumentBridge {
    private val entries = mutableMapOf<BridgeKey<*>, Any>()


    /**
     * Self-constructing pub/sub channel, lazily created on first touch via [BridgeKey.create].
     * Whichever sibling touches the key first creates the instance; the other gets the same one.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> channel(key: BridgeKey<T>): T =
        entries.getOrPut(key) {
            (key.create() ?: error("BridgeKey has no factory; use provide(): $key")) as Any
        } as T


    /**
     * Register an owner-constructed instance (e.g. a store needing constructor dependencies). The
     * owning controller calls this in `render()` — an idempotent map write that triggers no
     * re-render and runs before any child `componentDidMount`, so descendants can [lookup] it.
     */
    fun <T> provide(key: BridgeKey<T>, value: T) {
        entries[key] = value as Any
    }


    /**
     * Read an owner-[provide]d instance, or null if not yet provided (e.g. a different document type
     * is mounted, or the owner hasn't rendered yet).
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> lookup(key: BridgeKey<T>): T? =
        entries[key] as T?
}
