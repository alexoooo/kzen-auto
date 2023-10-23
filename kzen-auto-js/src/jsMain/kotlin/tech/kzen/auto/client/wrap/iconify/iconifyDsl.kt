package tech.kzen.auto.client.wrap.iconify

import react.ChildrenBuilder
import react.react


fun ChildrenBuilder.iconify(module: IconifyIconModule) {
    IconifyIcon::class.react {
        icon = module.default
    }
}