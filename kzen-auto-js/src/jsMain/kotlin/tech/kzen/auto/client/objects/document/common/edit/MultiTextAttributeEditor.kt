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
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.platform.collect.toPersistentList
import web.html.HTMLTextAreaElement


//---------------------------------------------------------------------------------------------------------------------
@Suppress("PropertyName")
external interface MultiTextAttributeEditorProps: Props {
    var objectLocation: ObjectLocation
    var attributePath: AttributePath

    var value: Collection<String>
    var unique: Boolean

    var labelOverride: String?
    var InputProps: InputBaseProps?
//    var style: Json?
    var rows: Int?
    var maxRows: Int?

    var disabled: Boolean
    var invalid: Boolean

    var onChange: ((List<String>) -> Unit)?
}


external interface MultiTextAttributeEditorState: State {
    var value: List<String>
    var pending: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class MultiTextAttributeEditor(
    props: MultiTextAttributeEditorProps
):
    RPureComponent<MultiTextAttributeEditorProps, MultiTextAttributeEditorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
        async {
            submitEdit()
        }
    }, 1000)


    //-----------------------------------------------------------------------------------------------------------------
    override fun MultiTextAttributeEditorState.init(props: MultiTextAttributeEditorProps) {
        this.value = props.value.toList()
        pending = false
    }


    override fun componentDidUpdate(
        prevProps: MultiTextAttributeEditorProps,
        prevState: MultiTextAttributeEditorState,
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
    override fun ChildrenBuilder.render() {
        TextField {
            name = "${props.objectLocation.asString()} - ${props.attributePath.asString()}"

            fullWidth = true
            multiline = true
            size = Size.small
//            autoComplete

            label = ReactNode(formattedLabel() + " (one per line)")
            value = state.value.joinToString("\n")

            onChange = {
                val value = (it.target as HTMLTextAreaElement).value
                onValueChange(value)
            }

            disabled = props.disabled
            error = props.invalid

            if (props.InputProps != null) {
                InputProps = props.InputProps!!
            }

//            if (props.style != null) {
//                @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
//                style = props.style!! as Properties
//            }
            if (props.rows != null) {
                rows = props.rows!!
            }
            if (props.maxRows != null) {
                maxRows = props.maxRows!!
            }
        }
    }
}