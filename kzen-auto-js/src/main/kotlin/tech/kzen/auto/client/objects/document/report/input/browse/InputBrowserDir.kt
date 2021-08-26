package tech.kzen.auto.client.objects.document.report.input.browse

import kotlinx.css.*
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.*
import react.dom.attrs
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.report.state.ListInputsBrowserNavigate
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.client.wrap.material.*
import tech.kzen.auto.common.util.data.DataLocation


class InputBrowserDir(
    props: Props
):
    RPureComponent<InputBrowserDir.Props, InputBrowserDir.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
        var editDisabled: Boolean
        var browseDir: DataLocation
        var errorMode: Boolean
    }


    interface State: react.State {
        var textEdit: Boolean
        var editDir: String
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        textEdit = false
        editDir = props.browseDir.asString()
    }


    override fun componentDidUpdate(
        prevProps: Props,
        prevState: State,
        snapshot: Any
    ) {
        if (! state.textEdit) {
            setState {
                editDir = props.browseDir.asString()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onEditToggle() {
        setState {
            textEdit = ! textEdit
        }
    }


    private fun onEditChange(newValue: String) {
//        console.log("$$# onEditChange - $newValue")
        setState {
            editDir = newValue
        }
    }


    private fun onEditSubmit() {
        if (state.editDir != props.browseDir.asString()) {
            props.dispatcher.dispatchAsync(ListInputsBrowserNavigate(DataLocation.of(state.editDir)))
        }

        setState {
            textEdit = false
        }
    }


    private fun onDirSelected(dir: DataLocation) {
        if (props.editDisabled) {
            return
        }

        props.dispatcher.dispatchAsync(ListInputsBrowserNavigate(dir))

        setState {
            editDir = dir.asString()
        }
    }


    private fun handleEnterAndEscape(event: KeyboardEvent) {
        ClientInputUtils.handleEnterAndEscape(
            event, ::onEditSubmit, ::onEditToggle)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        if (state.textEdit) {
            renderTextEdit()
        }
        else {
            renderBreadCrumbs()
        }
    }


    private fun RBuilder.renderBreadCrumbs() {
//        val parts = DataLocationInfo.split(props.browseDir)
        val parts = props.browseDir.ancestors()

        styledDiv {
            css {
//                display = Display.inlineBlock
//                backgroundColor = Color.blue
                position = Position.relative
            }

            for ((index, part) in parts.withIndex()) {
                styledSpan {
                    key = part.asString()

                    if (index != 0) {
                        child(ArrowForwardIosIcon::class) {
                            attrs {
                                style = reactStyle {
                                    fontSize = 0.75.em
                                    marginLeft = 0.25.em
                                    marginRight = 0.25.em
                                    color = Color.grey
                                }
                            }
                        }
                    }

                    styledSpan {
                        css {
                            paddingLeft = 0.25.em
                            paddingRight = 0.25.em
                            fontSize = 1.25.em
                            cursor = Cursor.pointer
                            hover {
                                backgroundColor = Color.lightGrey
                            }

                            if (props.errorMode) {
                                color = Color.red
                            }
                        }

                        attrs {
                            title = part.asString()

                            onClickFunction = {
                                onDirSelected(part)
                            }
                        }

                        +part.fileName()
                    }
                }
            }

            styledSpan {
                css {
                    position = Position.absolute
                    top = 0.px
                    right = 0.px
                }

                child(MaterialIconButton::class) {
                    attrs {
                        style = reactStyle {
                            marginTop = (-0.6).em
                            zIndex = 1000
                        }

                        title = "Edit path"

                        onClick = {
                            onEditToggle()
                        }
                    }

                    child(EditIcon::class) {
                        attrs {
                            style = reactStyle {
//                                fontSize = 0.85.em
                            }
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderTextEdit() {
        styledDiv {
            child(MaterialTextField::class) {
                attrs {
                    fullWidth = true

                    label = "Path"
                    value = state.editDir

                    onChange = {
                        val target = it.target as HTMLInputElement
                        onEditChange(target.value)
                    }

                    disabled = props.editDisabled
                    error = props.errorMode

                    style = reactStyle {
                        width = 100.pct.minus(6.em)
                    }

                    onKeyDown = ::handleEnterAndEscape
                }
            }

            child(MaterialIconButton::class) {
                attrs {
                    style = reactStyle {
//                        marginTop = 1.em
                    }
                    onClick = {
                        onEditToggle()
                    }
                    title = "Cancel"
                }

                child(CancelIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 0.85.em
                        }
                    }
                }
            }

            child(MaterialIconButton::class) {
                attrs {
                    style = reactStyle {
//                        marginTop = 1.em
                    }
                    onClick = {
                        onEditSubmit()
                    }
                    title = "Save"
                }

                child(SaveIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 0.85.em
                        }
                    }
                }
            }
        }
    }
}