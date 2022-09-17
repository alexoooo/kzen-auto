package tech.kzen.auto.client.wrap.material

import react.ReactElement
import kotlin.js.Json

//fun RBuilder.materialButton(handler: RHandler<MaterialButtonProps>) = child(MaterialButton::class) {
//    attrs {
//        this.variant = "raised"
//        this.style = ButtonStyle("#f47421", "#FFFFFF")
//    }
//    handler()
//}


external interface NestedInputProps: react.Props {
//        val startAdornment: MaterialInputAdornment
    var startAdornment: ReactElement<react.Props>
}

class NestedInputLabelProps(
    val style: Json? = null,
    val shrink: Boolean? = null
)

class FadeTimeout(
    val appear: Int = 0,
    val enter: Int = 0,
    val exit: Int = 0
)

//class ButtonStyle(val backgroundColor: String, val color: String)
//
//fun RBuilder.materialTextField(handler: RHandler<MaterialTextFieldProps>) = child(MaterialTextField::class) {
//    attrs { margin = "normal" }
//    handler()
//}
//
//fun RBuilder.materialFormControl(handler: RHandler<MaterialFormControlProps>) = child(MaterialFormControl::class, handler)
//
//fun RBuilder.materialSelect(handler: RHandler<MaterialSelectProps>) = child(MaterialSelect::class, handler)
//
//fun RBuilder.materialMenuItem(handler: RHandler<MaterialMenuItemProps>) = child(MaterialMenuItem::class, handler)
//
//fun RBuilder.materialInputLabel(handler: RHandler<MaterialInputLabelProps>) = child(MaterialInputLabel::class, handler)
