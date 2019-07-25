@file:JsModule("react-select")
package tech.kzen.auto.client.wrap

import org.w3c.dom.HTMLElement
import react.Component
import react.RProps
import react.RState
import react.ReactElement
import kotlin.js.Json


// see: https://codesandbox.io/s/ly87zo23kl
@JsName("default")
external class ReactSelect: Component<ReactSelectProps, RState> {
    override fun render(): ReactElement?
}


external interface ReactSelectProps: RProps {
    var id: String

    var value: ReactSelectOption?

    var options: Array<ReactSelectOption>

    var onChange: (ReactSelectOption) -> Unit

    var components: Json

    var menuContainerStyle: Json

    var styles: Json

    var menuPortalTarget: HTMLElement

//    var id: String
//    var variant: String
//    var color: String
//    var style: ButtonStyle
//    var size: String
//    var onClick: () -> Unit
}


