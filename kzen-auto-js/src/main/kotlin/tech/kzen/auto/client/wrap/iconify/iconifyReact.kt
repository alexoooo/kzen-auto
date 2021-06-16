@file:JsModule("@iconify/react")
package tech.kzen.auto.client.wrap.iconify

import react.Component
import react.RProps
import react.RState
import react.ReactElement


//---------------------------------------------------------------------------------------------------------------------
@JsName("Icon")
external class IconifyIcon: Component<IconifyProps, RState> {
    override fun render(): ReactElement?
}


//@JsName("InlineIcon")
//external class IconifyInlineIcon: Component<IconifyProps, RState> {
//    override fun render(): ReactElement?
//}


external interface IconifyProps: RProps {
    var icon: IconifyIconData

//    var title: String
//    var color: String
//    var style: Json
//
//    var onClick: () -> Unit
}



external interface IconifyIconModule {
    val default: IconifyIconData
}

external interface IconifyIconData {
    val body: String
    val width: Int
    val height: Int
}