@file:JsModule("@material-ui/icons")
package tech.kzen.auto.client.wrap


import react.Component
import react.RProps
import react.RState
import react.ReactElement
import kotlin.js.Json


// see: https://material-ui.com/style/icons/
// see: https://material.io/tools/icons/?style=baseline


// NB: can't create common MaterialIcon interface because 'external' doesn't support that

external interface IconProps : RProps {
    var color: String
    var style: Json
}


@JsName("Delete")
external class DeleteIcon : Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("PlayArrow")
external class PlayArrowIcon : Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("KeyboardArrowUp")
external class KeyboardArrowUpIcon : Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("KeyboardArrowDown")
external class KeyboardArrowDownIcon : Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Save")
external class SaveIcon : Component<IconProps, RState> {
    override fun render(): ReactElement?
}


@JsName("Cancel")
external class CancelIcon : Component<IconProps, RState> {
    override fun render(): ReactElement?
}

@JsName("ArrowDownward")
external class ArrowDownwardIcon : Component<IconProps, RState> {
    override fun render(): ReactElement?
}



