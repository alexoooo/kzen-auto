@file:JsModule("@material-ui/lab")
package tech.kzen.auto.client.wrap


import org.w3c.dom.events.Event
import react.Component
import react.RProps
import react.RState
import react.ReactElement


@JsName("ToggleButton")
external class MaterialToggleButton: Component<ToggleButtonProps, RState> {
    override fun render(): ReactElement?
}

external interface ToggleButtonProps: RProps {
    var value: String
    var disabled: Boolean
}


@JsName("ToggleButtonGroup")
external class MaterialToggleButtonGroup: Component<ToggleButtonGroupProps, RState> {
    override fun render(): ReactElement?
}

external interface ToggleButtonGroupProps: RProps {
    var value: String
    var exclusive: Boolean
    var onChange: (e: Event, v: Any?) -> Unit
}

