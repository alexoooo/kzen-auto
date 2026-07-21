package tech.kzen.auto.client.objects.document.common.edit

import mui.material.InputBaseProps
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.onChange
import tech.kzen.auto.client.wrap.*
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.store.MirroredGraphStore
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

    var mirroredGraphStore: MirroredGraphStore
}


external interface MultiTextAttributeEditorState: State {
    var value: List<String>

    // Non-null once a write failed, turning the field red; the message itself is carried by the global banner.
    var errorMessage: String?
}


//---------------------------------------------------------------------------------------------------------------------
class MultiTextAttributeEditor(
    props: MultiTextAttributeEditorProps
):
    RPureComponent<MultiTextAttributeEditorProps, MultiTextAttributeEditorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    // NB: `this.props` - see the shadowing note in TextAttributeEditor.
    private val committer = AttributeCommitter(
        graphStore = { this.props.mirroredGraphStore },
        objectLocation = { this.props.objectLocation },
        attributePath = { this.props.attributePath },
        pendingNotation = {
            val adjustedValues =
                if (this.props.unique) {
                    state.value.toSet().toList()
                }
                else {
                    state.value
                }

            ListAttributeNotation(adjustedValues
                .map { ScalarAttributeNotation(it) }
                .toPersistentList())
        },
        // Read back out of the committed notation rather than off state, which may have moved on during the apply.
        onCommitted = { notation ->
            this.props.onChange?.invoke((notation as ListAttributeNotation).values.map { it.asString()!! })
        },
        onError = { message -> setState { errorMessage = message } })


    //-----------------------------------------------------------------------------------------------------------------
    override fun MultiTextAttributeEditorState.init(props: MultiTextAttributeEditorProps) {
        this.value = props.value.toList()
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
    override fun componentWillUnmount() {
        committer.flush()
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
        }

        committer.schedule()
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

            // Commit the pending debounced edit on focus loss, so a following separate command is
            // sequenced after this write rather than racing it (see DebouncedSubmitter's invariant).
            onBlur = { committer.flush() }

            disabled = props.disabled
            error = props.invalid || state.errorMessage != null

            if (props.InputProps != null) {
                inputSlotProps = props.InputProps!!
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