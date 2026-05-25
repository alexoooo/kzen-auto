package tech.kzen.auto.client.objects.document.report.input.browse

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import mui.material.Size
import mui.material.TextField
import mui.system.sx
import react.ChildrenBuilder
import react.Key
import react.ReactNode
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.onChange
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.iconByName
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.*
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface InputBrowserPathControllerProps: react.Props {
    var mainLocation: ObjectLocation
    var browseDir: DataLocation
    var errorMode: Boolean
    var inputStore: ReportInputStore
}


external interface InputBrowserPathControllerState: react.State {
    var textEdit: Boolean
    var editDir: String
    var hover: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class InputBrowserPathController(
    props: InputBrowserPathControllerProps
):
    RPureComponent<InputBrowserPathControllerProps, InputBrowserPathControllerState>(props)
{

    //-----------------------------------------------------------------------------------------------------------------
    override fun InputBrowserPathControllerState.init(props: InputBrowserPathControllerProps) {
        textEdit = false
        editDir = props.browseDir.asString()
        hover = false
    }


    override fun componentDidUpdate(
        prevProps: InputBrowserPathControllerProps,
        prevState: InputBrowserPathControllerState,
        snapshot: Any
    ) {
        if (!state.textEdit) {
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
        val nextTextEdit = state.textEdit
        setState {
            textEdit = !nextTextEdit
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


    private fun handleEnterAndEscape(event: react.dom.events.KeyboardEvent<*>) {
        ClientInputUtils.handleEnterAndEscape(
            event, ::onEditSubmit, ::onEditToggle)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        if (state.textEdit) {
            renderTextEdit()
        }
        else {
            renderBreadCrumbs()
        }
    }


    private fun ChildrenBuilder.renderBreadCrumbs() {
        val parts = props.browseDir.ancestors()

        div {
            onMouseOver = {
                onHoverIn()
            }
            onMouseOut = {
                onHoverOut()
            }

            for ((index, part) in parts.withIndex()) {
                span {
                    key = Key(part.asString())

                    if (index != 0) {
                        iconByName("ArrowForwardIos") {
                            style = unsafeJso {
                                fontSize = 0.75.em
                                marginLeft = 0.25.em
                                marginRight = 0.25.em
                                color = NamedColor.grey
                            }
                        }
                    }

                    span {
                        css {
                            paddingLeft = 0.25.em
                            paddingRight = 0.25.em
                            fontSize = 1.25.em
                            cursor = Cursor.pointer
                            hover {
                                backgroundColor = NamedColor.lightgrey
                            }

                            if (props.errorMode) {
                                color = NamedColor.red
                            }
                        }

                        title = part.asString()

                        onClick = {
                            onDirSelected(part)
                        }

                        +part.fileName()
                    }
                }
            }

            span {
                css {
                    position = Position.relative
                    marginLeft = 0.5.em

                    if (!state.hover) {
                        display = None.none
                    }
                }

                span {
                    css {
                        position = Position.absolute
                        top = (-7).px
                        left = 0.px
                    }

                    IconButton  {
                        size = Size.small
                        css {
                            zIndex = integer(1000)
                        }

                        title = "Edit path"

                        onClick = {
                            onEditToggle()
                        }

                        iconByName("Edit") {}
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderTextEdit() {
        div {
            TextField {
                fullWidth = true
                size = Size.small
                sx {
                    width = 100.pct.minus(6.em)
                }

                label = ReactNode("Path")
                value = state.editDir

                onChange = {
                    val target = it.target as HTMLInputElement
                    onEditChange(target.value)
                }

                error = props.errorMode
                onKeyDown = { e -> handleEnterAndEscape(e) }
            }

            IconButton {
                onClick = {
                    onEditToggle()
                }
                title = "Cancel"

                iconByName("Cancel") {
                    style = unsafeJso {
                        fontSize = 0.85.em
                    }
                }
            }

            IconButton {
                onClick = {
                    onEditSubmit()
                }
                title = "Save"

                iconByName("Save") {
                    style = unsafeJso {
                        fontSize = 0.85.em
                    }
                }
            }
        }
    }
}