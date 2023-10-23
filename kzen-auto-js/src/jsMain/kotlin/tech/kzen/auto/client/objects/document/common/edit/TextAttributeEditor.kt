package tech.kzen.auto.client.objects.document.common.edit

import mui.material.InputBaseProps
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.onChange
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import web.html.HTMLInputElement
import web.html.HTMLTextAreaElement


//---------------------------------------------------------------------------------------------------------------------
external interface TextAttributeEditorProps: Props {
    var objectLocation: ObjectLocation
    var attributePath: AttributePath

    var value: Any
    var type: TextAttributeEditor.Type?

    var labelOverride: String?

    @Suppress("PropertyName")
    var InputProps: InputBaseProps?

    var disabled: Boolean
    var invalid: Boolean

    var onChange: ((String) -> Unit)?
}


external interface TextAttributeEditorState: State {
    var value: String
    var pending: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class TextAttributeEditor(
    props: TextAttributeEditorProps
):
    RPureComponent<TextAttributeEditorProps, TextAttributeEditorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    enum class Type {
        PlainText,
        MultilineText,
        Number
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
        async {
            submitEdit()
        }
    }, 1000)


    //-----------------------------------------------------------------------------------------------------------------
    override fun TextAttributeEditorState.init(props: TextAttributeEditorProps) {
        value = stateText(props.value, props.type)
        pending = false
    }


    override fun componentDidUpdate(
        prevProps: TextAttributeEditorProps,
        prevState: TextAttributeEditorState,
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
    override fun ChildrenBuilder.render() {
        val valueType = props.type ?: Type.PlainText

        val isMultiline = valueType == Type.MultilineText

        TextField {
            fullWidth = true
            multiline = isMultiline
            size = Size.small

            label = ReactNode(formattedLabel())
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