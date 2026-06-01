@file:JsModule("@iconify/react")
package tech.kzen.auto.client.wrap.iconify

import react.Component
import react.ReactElement
import kotlin.js.Promise


//---------------------------------------------------------------------------------------------------------------------
// The @iconify/react <Icon> component, rendered by name. Icon data is supplied by the custom loader
// registered in IconLoader (which fetches it from the JVM backend on demand and caches it), so the
// bundle carries no icon data.
@JsName("Icon")
external class IconifyIcon: Component<IconifyProps, react.State> {
    override fun render(): ReactElement<IconifyProps>?
}


external interface IconifyProps: react.Props {
    var icon: String

    // Mirrors the props the former @mui IconProps exposed, so existing icon { } blocks keep compiling.
    var title: String
    var style: react.CSSProperties?
    var onClick: () -> Unit
}


//---------------------------------------------------------------------------------------------------------------------
// Named exports of @iconify/react used to wire on-demand loading (see IconLoader).

// Registers a per-prefix batch loader: <Icon> collects the missing names rendered within a tick and calls
// this once with the array; the returned IconifyJSON is cached so subsequent renders resolve synchronously.
external fun setCustomIconsLoader(
    loader: (icons: Array<String>, prefix: String, provider: String) -> Promise<dynamic>,
    prefix: String,
    provider: String = definedExternally)

// Registers a whole IconifyJSON collection up front (used for the startup preload of always-visible icons).
external fun addCollection(data: dynamic, provider: String = definedExternally): Boolean
