package tech.kzen.auto.client.objects.document.common.edit

import org.w3c.dom.HTMLTextAreaElement
import react.*
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.client.wrap.material.MaterialTextField
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.platform.collect.toPersistentList
import kotlin.js.Json


class MultiTextAttributeEditor(
    props: Props
):
    RPureComponent<MultiTextAttributeEditor.Props, MultiTextAttributeEditor.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
//    enum class Type {
//        PlainText,
//        MultilineText,
//        Number
//    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var objectLocation: ObjectLocation
        var attributePath: AttributePath

        var value: Collection<String>
        var unique: Boolean

        var labelOverride: String?
        var InputProps: react.Props?
        var style: Json?
        var rows: Int?
        var maxRows: Int?

        var disabled: Boolean
        var invalid: Boolean

        var onChange: ((List<String>) -> Unit)?
    }


    interface State: react.State {
        var value: List<String>
        var pending: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
        async {
            submitEdit()
        }
    }, 1000)


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        this.value = props.value.toList()
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
            this.value = props.value.toList()
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
        val lines = newValue.split(Regex("\\n+"))
        val parsedValue =
            if (lines.size == 1 && lines[0].isEmpty()) {
                listOf()
            }
            else {
                lines
            }

        setState {
            value = parsedValue
            pending = true
        }

        submitDebounce.apply()
    }


    private suspend fun submitEdit() {
        val adjustedValues =
            if (props.unique) {
                state.value.toSet().toList()
            }
            else {
                state.value
            }

        val attributeNotation =
            ListAttributeNotation(adjustedValues
                .map { ScalarAttributeNotation(it) }
                .toPersistentList())

//        val command = CommonEditUtils.editCommand(
//            props.objectLocation, props.attributePath, attributeNotation)
//
//        // TODO: handle error
//        ClientContext.mirroredGraphStore.apply(command)
//
//        setState {
//            pending = false
//        }
//
//        props.onChange?.invoke(attributeNotation)

//        val attributeNotation = ScalarAttributeNotation(state.value)
//
        val command = CommonEditUtils.editCommand(
            props.objectLocation, props.attributePath, attributeNotation)

        // TODO: handle error
        ClientContext.mirroredGraphStore.apply(command)

        setState {
            pending = false
        }

        props.onChange?.invoke(adjustedValues)
    }


    private fun formattedLabel(): String {
        return CommonEditUtils.formattedLabel(props.attributePath, props.labelOverride)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        val valueType = props.type ?: Type.PlainText
//
//        val isMultiline = valueType == Type.MultilineText

        child(MaterialTextField::class) {
            attrs {
                fullWidth = true
                multiline = true

                label = formattedLabel() + " (one per line)"
                value = state.value.joinToString("\n")

                onChange = {
                    val value =
                        (it.target as HTMLTextAreaElement).value

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

                if (props.style != null) {
                    style = props.style!!
                }
                if (props.rows != null) {
                    rows = props.rows!!
                }
                if (props.maxRows != null) {
                    maxRows = props.maxRows!!
                }
            }
        }
    }
}