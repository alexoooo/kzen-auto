package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.onChange
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.edit.DebouncedSubmitter
import tech.kzen.auto.client.objects.document.common.edit.documentEditActivity
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.inputLabelSlotProps
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.setState
import web.cssom.AlignItems
import web.cssom.Color
import web.cssom.Display
import web.cssom.FontFamily
import web.cssom.em
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface PathProjectionEntryRowProps: Props {
    var index: Int
    var path: String
    var alias: String
    var outputName: String
    var error: String?
    var onAlias: (Int, String) -> Unit
    var onRemove: (Int) -> Unit
}


external interface PathProjectionEntryRowState: State {
    var alias: String
}


//---------------------------------------------------------------------------------------------------------------------
// One chosen path of a PathProjectionWorker's `paths` list: [remove] path → output name, an alias field (debounced
// like FormulaMapRow, flushed on blur and unmount) and the binding error the shared PathBinding reported for it —
// an upstream shape change shows here as the named invalid path.
class PathProjectionEntryRow(
    props: PathProjectionEntryRowProps
):
    RPureComponent<PathProjectionEntryRowProps, PathProjectionEntryRowState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private val submitter = DebouncedSubmitter(editActivity = { documentEditActivity() }) { onSubmitEdit() }


    init {
        installContextType(DocumentBridgeContext)
    }


    override fun PathProjectionEntryRowState.init(props: PathProjectionEntryRowProps) {
        alias = props.alias
    }


    override fun componentDidUpdate(
        prevProps: PathProjectionEntryRowProps,
        prevState: PathProjectionEntryRowState,
        snapshot: Any
    ) {
        // A committed alias from elsewhere (undo, another client) replaces an idle draft
        if (props.alias != prevProps.alias && state.alias == prevProps.alias) {
            setState { alias = props.alias }
        }
    }


    override fun componentWillUnmount() {
        submitter.flush()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onSubmitEdit() {
        if (state.alias == props.alias) {
            return
        }
        props.onAlias(props.index, state.alias)
    }


    private fun onAliasChange(value: String) {
        setState { alias = value }
        submitter.schedule()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                marginBottom = 0.25.em
            }
            IconButton {
                title = "Remove path"
                onClick = { props.onRemove(props.index) }
                icon("material-symbols:delete") {}
            }
            span {
                css {
                    fontFamily = FontFamily.monospace
                    fontSize = 0.85.em
                    marginRight = 0.5.em
                }
                +props.path
            }
            span {
                css {
                    fontSize = 0.8.em
                    color = Color("rgba(0, 0, 0, 0.6)")
                    marginRight = 0.5.em
                }
                +"→ ${props.outputName}"
            }
            TextField {
                size = Size.small
                label = ReactNode("as")
                value = state.alias
                onChange = {
                    onAliasChange((it.target as HTMLInputElement).value)
                }
                onBlur = { submitter.flush() }
                inputLabelSlotProps = unsafeJso {
                    shrink = true
                }
            }
        }
        props.error?.let { message ->
            div {
                css {
                    fontSize = 0.8.em
                    color = Color("#c62828")
                    marginLeft = 2.5.em
                    marginBottom = 0.25.em
                }
                +message
            }
        }
    }
}
