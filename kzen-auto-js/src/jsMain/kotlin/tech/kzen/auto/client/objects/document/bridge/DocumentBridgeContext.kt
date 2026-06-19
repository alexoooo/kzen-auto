package tech.kzen.auto.client.objects.document.bridge

import react.createContext


/**
 * The single per-document React context. Its value is the [DocumentBridge] for the currently mounted
 * document, provided by `ProjectController` (the lowest common ancestor of header and stage). Every
 * per-document class component installs exactly this one `contextType` (via
 * `wrap/React.kt` `installContextType` / `contextValue`) and reaches stores and channels by key.
 *
 * Null default for the same reason as the former `ScriptStoreContext`: a descendant may render before
 * a provider exists upstream.
 */
val DocumentBridgeContext = createContext<DocumentBridge?>(null)
