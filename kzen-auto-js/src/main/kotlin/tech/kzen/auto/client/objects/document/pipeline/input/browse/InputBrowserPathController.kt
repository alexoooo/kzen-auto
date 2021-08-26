package tech.kzen.auto.client.objects.document.pipeline.input.browse

import kotlinx.css.*
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.*
import react.dom.attrs
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputStore
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.wrap.material.*
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.model.locate.ObjectLocation


class InputBrowserPathController(
    props: Props
):
    RPureComponent<InputBrowserPathController.Props, InputBrowserPathController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var mainLocation: ObjectLocation
        var browseDir: DataLocation
        var errorMode: Boolean
        var inputStore: PipelineInputStore
    }


    interface State: react.State {
        var textEdit: Boolean
        var editDir: String
        var hover: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        textEdit = false
        editDir = props.browseDir.asString()
        hover = false
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
    private fun onHoverIn() {
        setState {
            hover = true
        }
    }


    private fun onHoverOut() {
        setState {
            hover = false
        }
    }


    private fun onEditToggle() {
        setState {
            textEdit = ! textEdit
        }
    }


    private fun onEditChange(newValue: String) {
        setState {
            editDir = newValue
        }
    }


    private fun onEditSubmit() {
        if (state.editDir != props.browseDir.asString()) {
            onDirSelected(DataLocation.of(state.editDir))
        }

        setState {
            textEdit = false
        }
    }


    private fun onDirSelected(dir: DataLocation) {
        props.inputStore.browser.browserDirSelectedAsync(dir)
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
        val parts = props.browseDir.ancestors()

        styledDiv {
            attrs {
                onMouseOverFunction = {
                    onHoverIn()
                }
                onMouseOutFunction = {
                    onHoverOut()
                }
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
                    position = Position.relative
                    marginLeft = 0.5.em

                    if (! state.hover) {
                        display = Display.none
                    }
                }

                styledSpan {
                    css {
                        position = Position.absolute
                        top = (-7).px
                        left = 0.px
                    }

                    child(MaterialIconButton::class) {
                        attrs {
                            size = "small"

                            style = reactStyle {
                                zIndex = 1000
                            }

                            title = "Edit path"

                            onClick = {
                                onEditToggle()
                            }
                        }

                        child(EditIcon::class) {}
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

//                    disabled = props.editDisabled
                    error = props.errorMode

                    style = reactStyle {
                        width = 100.pct.minus(6.em)
                    }

                    onKeyDown = ::handleEnterAndEscape
                }
            }

            child(MaterialIconButton::class) {
                attrs {
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