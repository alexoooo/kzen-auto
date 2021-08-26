@file:JsModule("@material-ui/lab")
package tech.kzen.auto.client.wrap.material


import org.w3c.dom.events.Event
import react.Component
import react.ReactElement
import kotlin.js.Json


@JsName("ToggleButton")
external class MaterialToggleButton: Component<ToggleButtonProps, react.State> {
    override fun render(): ReactElement?
}

external interface ToggleButtonProps: react.Props {
    var value: String
    var disabled: Boolean
    var size: String
    var style: Json
}


@JsName("ToggleButtonGroup")
external class MaterialToggleButtonGroup: Component<ToggleButtonGroupProps, react.State> {
    override fun render(): ReactElement?
}

external interface ToggleButtonGroupProps: react.Props {
    var value: String
    var exclusive: Boolean
    var onChange: (e: Event, v: Any?) -> Unit
    var size: String
    var style: Json
}


@JsName("ToggleButtonGroup")
external class MaterialToggleButtonMultiGroup: Component<ToggleButtonGroupMultiProps, react.State> {
    override fun render(): ReactElement?
}

external interface ToggleButtonGroupMultiProps: react.Props {
    var value: Array<String>
    var exclusive: Boolean
    var onChange: (e: Event, v: Array<String>) -> Unit
    var size: String
    var style: Json
}

