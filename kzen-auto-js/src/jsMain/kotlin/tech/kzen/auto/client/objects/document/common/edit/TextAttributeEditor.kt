package tech.kzen.auto.client.objects.document.common.edit

import mui.material.InputBaseProps
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.onChange
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.store.MirroredGraphStore
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

    var mirroredGraphStore: MirroredGraphStore
}


external interface TextAttributeEditorState: State {
    var value: String

    // Non-null once a write failed, turning the field red; the message itself is carried by the global banner.
    var errorMessage: String?
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
    // NB: `this.props`, not bare `props` - in a property initializer the primary-constructor parameter shadows the
    // inherited member, so a bare reference would pin the FIRST render's props object for the component's whole
    // life, defeating AttributeCommitter's "every argument is read at commit time" contract. An editor outlives a
    // rename of its host (the manager re-renders it in place with a new objectLocation), and a commit must target
    // the current one. Same shape in every AttributeCommitter adopter.
    private val committer = AttributeCommitter(
        graphStore = { this.props.mirroredGraphStore },
        objectLocation = { this.props.objectLocation },
        attributePath = { this.props.attributePath },
        pendingNotation = { ScalarAttributeNotation(state.value) },
        onCommitted = { this.props.onChange?.invoke((it as ScalarAttributeNotation).value) },
        onError = { message -> setState { errorMessage = message } },
        editActivity = { documentEditActivity() })


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    override fun TextAttributeEditorState.init(props: TextAttributeEditorProps) {
        value = stateText(props.value, props.type)
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
    override fun componentWillUnmount() {
        committer.flush()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(newValue: String) {
        setState {
            value = newValue
        }

        committer.schedule()
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

            // Commit the pending debounced edit on focus loss, so a following separate command is
            // sequenced after this write rather than racing it (see DebouncedSubmitter's invariant).
            onBlur = { committer.flush() }

//                if (valueType == Type.Number) {
//                    type = InputType.number.name
//                }

            disabled = props.disabled
            error = props.invalid || state.errorMessage != null

            if (props.InputProps != null) {
                inputSlotProps = props.InputProps!!
            }
        }
    }
}