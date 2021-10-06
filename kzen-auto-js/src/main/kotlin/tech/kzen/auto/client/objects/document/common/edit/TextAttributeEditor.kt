package tech.kzen.auto.client.objects.document.common.edit

import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import react.*
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.client.wrap.material.MaterialTextField
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation


class TextAttributeEditor(
    props: Props
):
    RPureComponent<TextAttributeEditor.Props, TextAttributeEditor.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    enum class Type {
        PlainText,
        MultilineText,
        Number
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var objectLocation: ObjectLocation
        var attributePath: AttributePath

        var value: Any
        var type: Type?

        var labelOverride: String?
        var InputProps: react.Props?

        var disabled: Boolean
        var invalid: Boolean

        var onChange: ((String) -> Unit)?
    }


    class State(
        var value: String,
        var pending: Boolean
    ): react.State


    //-----------------------------------------------------------------------------------------------------------------
    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
        async {
            submitEdit()
        }
    }, 1000)


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        value = stateText(props.value, props.type)
        pending = false
    }


    override fun componentDidUpdate(
        prevProps: Props,
        prevState: State,
        snapshot: Any
    ) {
        if (props.value == prevProps.value) {
            return
        }

        setState {
            this.value = stateText(props.value, props.type)
        }
    }


    private fun stateText(value: Any, type: Type?): String {
        return when {
            type == Type.Number && value is Number ->
                if (value is Double || value is Float) {
                    value.toString()
                }
                else {
                    FormatUtils.decimalSeparator(value.toLong())
                }

            else ->
                value.toString()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun flush() {
        submitDebounce.cancel()
        if (state.pending) {
            submitEdit()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(newValue: String) {
        setState {
            value = newValue
            pending = true
        }

        submitDebounce.apply()
    }


    private suspend fun submitEdit() {
        val attributeNotation = ScalarAttributeNotation(state.value)

        val command = CommonEditUtils.editCommand(
            props.objectLocation, props.attributePath, attributeNotation)

        // TODO: handle error
        ClientContext.mirroredGraphStore.apply(command)

        setState {
            pending = false
        }

        props.onChange?.invoke(attributeNotation.value)
    }


    private fun formattedLabel(): String {
        return CommonEditUtils.formattedLabel(props.attributePath, props.labelOverride)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val valueType = props.type ?: Type.PlainText

        val isMultiline = valueType == Type.MultilineText

        child(MaterialTextField::class) {
            attrs {
                fullWidth = true
                multiline = isMultiline

                label = formattedLabel()
                value = state.value

                // https://stackoverflow.com/questions/54052525/how-to-change-material-ui-textfield-bottom-and-label-color-on-error-and-on-focus
//                InputLabelProps = NestedInputLabelProps(reactStyle {
//                    color = Color("rgb(66, 66, 66)")
//                })

                onChange = {
                    val value =
                        if (isMultiline) {
                            (it.target as HTMLTextAreaElement).value
                        }
                        else {
                            (it.target as HTMLInputElement).value
                        }

                    onValueChange(value)
                }

//                if (valueType == Type.Number) {
//                    type = InputType.number.name
//                }

                disabled = props.disabled
                error = props.invalid

                if (props.InputProps != null) {
                    InputProps = props.InputProps!!
                }
            }
        }
    }
}