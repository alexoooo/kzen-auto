package tech.kzen.auto.client.wrap.material

import react.ChildrenBuilder
import react.ComponentType


external interface IconProps: react.Props {
    var title: String
    var style: react.CSSProperties?
    var onClick: () -> Unit
}


// see: https://material-ui.com/style/icons/
// see: https://material.io/tools/icons/?style=baseline
//
// Webpack's require.context bundles every @mui/icons-material/<Name>.js into a context module,
// so any icon name (e.g. from notation data) resolves at runtime. Unknown names fall back to
// the Texture glyph, preserving the prior iconClassForName behaviour. The regex restricts the
// context to PascalCase root files, excluding the package's esm/, utils/ subpaths.
private val iconContext: dynamic =
    js("require.context('@mui/icons-material', false, /^\\.\\/[A-Z][A-Za-z0-9]+\\.js$/)")


fun iconType(name: String): ComponentType<IconProps> =
    try {
        iconContext("./$name.js").default.unsafeCast<ComponentType<IconProps>>()
    } catch (_: Throwable) {
        iconContext("./Texture.js").default.unsafeCast<ComponentType<IconProps>>()
    }


fun ChildrenBuilder.iconByName(name: String, block: IconProps.() -> Unit = {}) {
    iconType(name).invoke(block)
}
