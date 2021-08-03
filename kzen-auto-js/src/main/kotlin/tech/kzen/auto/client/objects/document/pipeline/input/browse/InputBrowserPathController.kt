package tech.kzen.auto.client.objects.document.pipeline.input.browse

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
    interface Props: RProps {
        var mainLocation: ObjectLocation
        var browseDir: DataLocation
        var errorMode: Boolean
        var inputStore: PipelineInputStore

//        var dirChangeError: String?
//        var onDirChange: (String?) -> Unit
    }


    interface State: RState {
        var textEdit: Boolean
        var editDir: String
//        var error: String?
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        textEdit = false
        editDir = props.browseDir.asString()
//        error = null
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
//        setState {
//            editDir = dir.asString()
//        }
//        dirSelectedAsync(dir)
        props.inputStore.browseDirSelectedAsync(dir)
    }


//    private fun dirSelectedAsync(dir: DataLocation) {
//        InputBrowserEndpoint.selectDirAsync(props.mainLocation, dir, props.onDirChange)
//    }


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
            css {
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

                    child(EditIcon::class) {}
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