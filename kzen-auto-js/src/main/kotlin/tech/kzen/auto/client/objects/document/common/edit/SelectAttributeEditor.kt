package tech.kzen.auto.client.objects.document.common.edit
//
//import kotlinx.browser.document
//import kotlinx.css.em
//import kotlinx.css.fontSize
//import kotlinx.css.width
//import react.Props
//import react.RBuilder
//import react.RPureComponent
//import react.State
//import tech.kzen.auto.client.service.ClientContext
//import tech.kzen.auto.client.util.async
//import tech.kzen.auto.client.wrap.material.MaterialInputLabel
//import tech.kzen.auto.client.wrap.reactStyle
//import tech.kzen.auto.client.wrap.select.ReactSelect
//import tech.kzen.auto.client.wrap.select.ReactSelectOption
//import tech.kzen.lib.common.model.attribute.AttributePath
//import tech.kzen.lib.common.model.locate.ObjectLocation
//import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
//import kotlin.js.Json
//import kotlin.js.json
//
//
////---------------------------------------------------------------------------------------------------------------------
//external interface SelectAttributeEditorProps: Props {
//    var objectLocation: ObjectLocation
//    var attributePath: AttributePath
//
//    var value: String
//    var options: Map<String, String>
//
//    var labelOverride: String?
//
//    var disabled: Boolean
//    var invalid: Boolean
//
//    var onChange: ((String) -> Unit)?
//}
//
//
////---------------------------------------------------------------------------------------------------------------------
//class SelectAttributeEditor(
//    props: SelectAttributeEditorProps
//):
//    RPureComponent<SelectAttributeEditorProps, State>(props)
//{
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun submitEditAsync(newValue: String) {
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
//    private suspend fun submitEdit(newValue: String) {
//        val attributeNotation = ScalarAttributeNotation(newValue)
//
//        val command = CommonEditUtils.editCommand(
//            props.objectLocation, props.attributePath, attributeNotation)
//
//        // TODO: handle error
//        ClientContext.mirroredGraphStore.apply(command)
//
//        props.onChange?.invoke(attributeNotation.value)
//    }
//
//
//    private fun formattedLabel(): String {
//        return CommonEditUtils.formattedLabel(props.attributePath, props.labelOverride)
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun RBuilder.render() {
//        val selectId = "material-react-select-edit"
//
//        child(MaterialInputLabel::class) {
//            attrs {
//                htmlFor = selectId
//
//                style = reactStyle {
//                    fontSize = 0.8.em
//                    width = 16.em
//                }
//            }
//
//            +formattedLabel()
//        }
//
//        val selectedOption = ReactSelectOption(props.value, props.options[props.value] ?: props.value)
//
//        val reactSelectOptions = props.options.map {
//            ReactSelectOption(it.key, it.value)
//        }
//
//        val selectOptions = reactSelectOptions.toTypedArray()
//
//        child(ReactSelect::class) {
//            attrs {
//                id = selectId
//                value = selectedOption
//                options = selectOptions
//
//                onChange = {
//                    submitEditAsync(it.value)
//                }
//
//                isDisabled = props.disabled
//
//                // https://stackoverflow.com/a/51844542/1941359
//                val styleTransformer: (Json, Json) -> Json = { base, _ ->
//                    val transformed = json()
//                    transformed.add(base)
//                    transformed["background"] = "transparent"
//                    transformed["borderWidth"] = "2px"
//                    transformed
//                }
//
//                val reactStyles = json()
//                reactStyles["control"] = styleTransformer
//                styles = reactStyles
//
//                menuPortalTarget = document.body!!
//            }
//        }
//    }
//}