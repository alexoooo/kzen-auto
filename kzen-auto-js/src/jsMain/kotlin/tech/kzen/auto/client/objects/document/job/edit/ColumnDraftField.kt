package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.div
import react.dom.onChange
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.edit.DebouncedSubmitter
import tech.kzen.auto.client.objects.document.common.edit.documentEditActivity
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.inputLabelSlotProps
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.setState
import web.cssom.Length
import web.html.HTMLInputElement
import web.html.HTMLTextAreaElement


//---------------------------------------------------------------------------------------------------------------------
external interface ColumnDraftFieldProps: Props {
    var value: String
    var label: String?
    var placeholder: String?
    var multiline: Boolean
    var error: Boolean
    var width: Length?
    var onCommit: (String) -> Unit
}


external interface ColumnDraftFieldState: State {
    var draft: String
}


//---------------------------------------------------------------------------------------------------------------------
// A text field of the writer columns table (an output name, or the allow / exclude patterns, one per line) whose
// draft commits debounced like PathProjectionEntryRow's alias, and on blur and unmount; a committed value from
// elsewhere (undo, another client) replaces an idle draft.
class ColumnDraftField(
    props: ColumnDraftFieldProps
):
    RPureComponent<ColumnDraftFieldProps, ColumnDraftFieldState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private val submitter = DebouncedSubmitter(editActivity = { documentEditActivity() }) { onSubmitEdit() }


    init {
        installContextType(DocumentBridgeContext)
    }


    override fun ColumnDraftFieldState.init(props: ColumnDraftFieldProps) {
        draft = props.value
    }


    override fun componentDidUpdate(
        prevProps: ColumnDraftFieldProps,
        prevState: ColumnDraftFieldState,
        snapshot: Any
    ) {
        if (props.value != prevProps.value && state.draft == prevProps.value) {
            setState { draft = props.value }
        }
    }


    override fun componentWillUnmount() {
        submitter.flush()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onSubmitEdit() {
        if (state.draft == props.value) {
            return
        }
        props.onCommit(state.draft)
    }


    private fun onDraftChange(value: String) {
        setState { draft = value }
        submitter.schedule()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            // A click in the field must not reach a row that toggles on click
            onClick = { it.stopPropagation() }
            props.width?.let { fieldWidth ->
                css { width = fieldWidth }
            }
            TextField {
                size = Size.small
                fullWidth = true
                props.label?.let { label = ReactNode(it) }
                props.placeholder?.let { placeholder = it }
                multiline = props.multiline
                if (props.multiline) {
                    minRows = 2
                    maxRows = 6
                }
                error = props.error
                value = state.draft
                onChange = {
                    val target = it.target
                    onDraftChange(
                        if (props.multiline) (target as HTMLTextAreaElement).value
                        else (target as HTMLInputElement).value)
                }
                onBlur = { submitter.flush() }
                inputLabelSlotProps = unsafeJso {
                    shrink = true
                }
            }
        }
    }
}
