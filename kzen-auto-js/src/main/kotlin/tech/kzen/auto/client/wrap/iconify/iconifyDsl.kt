package tech.kzen.auto.client.wrap.iconify

import react.RBuilder


fun RBuilder.iconify(module: IconifyIconModule) {
    child(IconifyIcon::class) {
        attrs {
            icon = module.default
        }
    }
}