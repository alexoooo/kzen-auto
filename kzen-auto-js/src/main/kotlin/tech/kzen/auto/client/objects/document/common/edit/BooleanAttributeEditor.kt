package tech.kzen.auto.client.objects.document.common.edit
//
//import kotlinx.css.Color
//import kotlinx.css.color
//import kotlinx.css.em
//import kotlinx.css.fontSize
//import org.w3c.dom.HTMLInputElement
//import react.Props
//import react.RBuilder
//import react.RPureComponent
//import react.State
//import tech.kzen.auto.client.service.ClientContext
//import tech.kzen.auto.client.util.async
//import tech.kzen.auto.client.wrap.material.MaterialInputLabel
//import tech.kzen.auto.client.wrap.material.MaterialSwitch
//import tech.kzen.auto.client.wrap.reactStyle
//import tech.kzen.lib.common.model.attribute.AttributePath
//import tech.kzen.lib.common.model.locate.ObjectLocation
//import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
//
//
////---------------------------------------------------------------------------------------------------------------------
//external interface BooleanAttributeEditorProps: Props {
//    var objectLocation: ObjectLocation
//    var attributePath: AttributePath
//
//    var value: Boolean
//
//    var trueLabelOverride: String?
//    var falseLabelOverride: String?
//
//    var disabled: Boolean
//
//    var onChange: ((Boolean) -> Unit)?
//}
//
//
////---------------------------------------------------------------------------------------------------------------------
//class BooleanAttributeEditor(
//    props: BooleanAttributeEditorProps
//):
//    RPureComponent<BooleanAttributeEditorProps, State>(props)
//{
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun submitEditAsync(newValue: Boolean) {
//        if (props.value == newValue) {
//            return
//        }
//
//        async {
//            submitEdit(newValue)
//        }
//    }
//
//
//    private suspend fun submitEdit(newValue: Boolean) {
//        val attributeNotation = ScalarAttributeNotation(newValue.toString())
//
//        val command = CommonEditUtils.editCommand(
//            props.objectLocation, props.attributePath, attributeNotation)
//
//        // TODO: handle error
//        ClientContext.mirroredGraphStore.apply(command)
//
//        props.onChange?.invoke(newValue)
//    }
//
//
//    private fun formattedLabel(): String {
//        val labelOverride = when (props.value) {
//            true -> props.trueLabelOverride
//            false -> props.falseLabelOverride
//        }
//
//        return CommonEditUtils.formattedLabel(props.attributePath, labelOverride)
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun RBuilder.render() {
//        val inputId = "material-react-switch-id"
//        child(MaterialInputLabel::class) {
//            attrs {
//                htmlFor = inputId
//
//                style = reactStyle {
//                    fontSize = 0.8.em
//                }
//            }
//
//            +formattedLabel()
//        }
//
//        child(MaterialSwitch::class) {
//            attrs {
//                id = inputId
//                checked = props.value
//                disabled = props.disabled
//                onChange = {
//                    val target = it.target as HTMLInputElement
//                    submitEditAsync(target.checked)
//                }
//                color = "default"
//
//                if (props.value) {
//                    style = reactStyle {
////                        this.color = Color("#8CBAE8")
//                        this.color = Color.black
//                    }
//                }
////                style = reactStyle {
////                    color = Color("#c4c4c4")
////                }
//
////                styleOverrides = json(
////                    "track" to reactStyle {
////                        color = Color.red
////                        backgroundColor = Color.green
////                    }
////                )
//            }
//        }
//    }
//}