package tech.kzen.auto.client.wrap

import react.RBuilder
import react.ReactElement


// todo: factor out class name, e.g. to specify css like size
fun iconByName(rBuilder: RBuilder, name: String): ReactElement? {
    return when (name) {
        "PlayArrow" ->
            rBuilder.child(PlayArrowIcon::class) {}

        "OpenInNew" ->
            rBuilder.child(OpenInNewIcon::class) {}

        "Http" ->
            rBuilder.child(HttpIcon::class) {}

        "Keyboard" ->
            rBuilder.child(KeyboardIcon::class) {}

        "TouchApp" ->
            rBuilder.child(TouchAppIcon::class) {}

        "Close" ->
            rBuilder.child(CloseIcon::class) {}

        "Message" ->
            rBuilder.child(MessageIcon::class) {}

        else ->
            rBuilder.child(TextureIcon::class) {}
    }
}