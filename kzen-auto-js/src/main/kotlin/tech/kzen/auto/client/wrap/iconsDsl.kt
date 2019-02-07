package tech.kzen.auto.client.wrap

import react.Component
import react.RBuilder
import react.RState
import react.ReactElement
import kotlin.reflect.KClass


fun iconClassForName(name: String): KClass<out Component<IconProps, RState>> {
    // TODO: is there a way to do this without manually writing these out?
    //  e.g. <Icon>edit_icon</Icon> in https://codesandbox.io/s/9yp4yk6qno

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

        "Timer" ->
            TimerIcon::class

        else ->
            TextureIcon::class
    }
}


fun RBuilder.iconByName(name: String): ReactElement? {
    return child(iconClassForName(name)) {}
}