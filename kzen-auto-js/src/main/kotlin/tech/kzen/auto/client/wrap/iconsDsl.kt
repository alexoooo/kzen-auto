package tech.kzen.auto.client.wrap

import react.Component
import react.RBuilder
import react.RState
import react.ReactElement
import kotlin.reflect.KClass


fun iconClassForName(name: String): KClass<out Component<IconProps, RState>> {
    return when (name) {
        "PlayArrow" ->
            PlayArrowIcon::class

        "OpenInNew" ->
            OpenInNewIcon::class

        "Http" ->
            HttpIcon::class

        "Keyboard" ->
            KeyboardIcon::class

        "TouchApp" ->
            TouchAppIcon::class

        "Close" ->
            CloseIcon::class

        "Message" ->
            MessageIcon::class

        else ->
            TextureIcon::class
    }
}


fun RBuilder.iconByName(name: String): ReactElement? {
    return child(iconClassForName(name)) {}
}