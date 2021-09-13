package tech.kzen.auto.client.objects.document.common.edit

import kotlinx.browser.document
import kotlinx.css.em
import kotlinx.css.fontSize
import kotlinx.css.width
import react.*
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.material.MaterialInputLabel
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import kotlin.js.Json
import kotlin.js.json


class SelectAttributeEditor(
    props: Props
):
    RPureComponent<SelectAttributeEditor.Props, SelectAttributeEditor.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var objectLocation: ObjectLocation
        var attributePath: AttributePath

        var value: String
        var options: Map<String, String>

        var labelOverride: String?
//        var InputProps: react.Props?

        var disabled: Boolean
        var invalid: Boolean

        var onChange: ((String) -> Unit)?
    }


    interface State: react.State {
//        var value: String
//        var pending: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
//        async {
//            submitEdit()
//        }
//    }, 1000)


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        this.value = props.value.toString()
//        pending = false
    }


//    override fun componentDidUpdate(
//        prevProps: Props,
//        prevState: State,
//        snapshot: Any
//    ) {
//        if (props.value == prevProps.value) {
//            return
//        }
//
//        setState {
//            this.value = props.value.toString()
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
//    suspend fun flush() {
//        submitDebounce.cancel()
//        if (state.pending) {
//            submitEdit()
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
//    private fun onValueChange(newValue: String) {
//        setState {
//            value = newValue
//            pending = true
//        }
//
//        submitDebounce.apply()
//    }


    private fun submitEditAsync(newValue: String) {
        if (props.value == newValue) {
            return
        }

        async {
            submitEdit(newValue)
        }
    }


    private suspend fun submitEdit(newValue: String) {
        val attributeNotation = ScalarAttributeNotation(newValue)

        val command =
            if (props.attributePath.nesting.segments.isEmpty()) {
                UpsertAttributeCommand(
                    props.objectLocation,
                    props.attributePath.attribute,
                    attributeNotation)
            }
            else {
                UpdateInAttributeCommand(
                    props.objectLocation,
                    props.attributePath,
                    attributeNotation)
            }

        // TODO: handle error
        ClientContext.mirroredGraphStore.apply(command)

//        setState {
//            pending = false
//        }

        props.onChange?.invoke(attributeNotation.value)
    }


    private fun formattedLabel(): String {
        return CommonEditUtils.formattedLabel(props.attributePath, props.labelOverride)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val selectId = "material-react-select-edit"

        child(MaterialInputLabel::class) {
            attrs {
                htmlFor = selectId

                style = reactStyle {
                    fontSize = 0.8.em
                    width = 16.em
                }
            }

            +formattedLabel()
        }

        val selectedOption = ReactSelectOption(props.value, props.options[props.value] ?: props.value)

        val reactSelectOptions = props.options.map {
            ReactSelectOption(it.key, it.value)
        }

        val selectOptions = reactSelectOptions.toTypedArray()

        child(ReactSelect::class) {
            attrs {
                id = selectId

                value = selectedOption

                options = selectOptions

                onChange = {
                    submitEditAsync(it.value)
                }

                // https://stackoverflow.com/a/51844542/1941359
                val styleTransformer: (Json, Json) -> Json = { base, _ ->
                    val transformed = json()
                    transformed.add(base)
                    transformed["background"] = "transparent"
                    transformed["borderWidth"] = "2px"
                    transformed
                }

                val reactStyles = json()
                reactStyles["control"] = styleTransformer
                styles = reactStyles

                menuPortalTarget = document.body!!
            }
        }


//        val valueType = props.type ?: Type.PlainText
//
//        val isMultiline = valueType == Type.MultilineText
//
//        child(MaterialTextField::class) {
//            attrs {
//                fullWidth = true
//                multiline = isMultiline
//
//                label = formattedLabel()
//                value = state.value
//
//                // https://stackoverflow.com/questions/54052525/how-to-change-material-ui-textfield-bottom-and-label-color-on-error-and-on-focus
////                InputLabelProps = NestedInputLabelProps(reactStyle {
////                    color = Color("rgb(66, 66, 66)")
////                })
//
//                onChange = {
//                    val value =
//                        if (isMultiline) {
//                            (it.target as HTMLTextAreaElement).value
//                        }
//                        else {
//                            (it.target as HTMLInputElement).value
//                        }
//
//                    onValueChange(value)
//                }
//
//                if (valueType == Type.Number) {
//                    type = InputType.number.name
//                }
//
//                disabled = props.disabled
//                error = props.invalid
//
//                if (props.InputProps != null) {
//                    InputProps = props.InputProps!!
//                }
//            }
//        }
    }
}