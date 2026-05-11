package tech.kzen.auto.client.wrap.iconify

import react.ChildrenBuilder
import tech.kzen.auto.client.wrap.react


fun ChildrenBuilder.iconify(module: IconifyIconModule) {
    IconifyIcon::class.react {
        icon = module.default
    }
}