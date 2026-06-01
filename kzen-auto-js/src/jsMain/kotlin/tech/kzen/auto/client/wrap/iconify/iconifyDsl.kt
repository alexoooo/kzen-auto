package tech.kzen.auto.client.wrap.iconify

import react.ChildrenBuilder
import tech.kzen.auto.client.wrap.react


// Single entry point for rendering an icon by name. Accepts a fully-qualified Iconify name
// ("material-symbols:settings"), a bare material-symbols name, or a legacy @mui PascalCase name from
// notation saved against the previous registry — all normalized by IconNames.resolve. The icon data is
// fetched on demand from the JVM backend by IconLoader.
fun ChildrenBuilder.icon(name: String, block: IconifyProps.() -> Unit = {}) {
    IconifyIcon::class.react {
        icon = IconNames.resolve(name)
        block()
    }
}
