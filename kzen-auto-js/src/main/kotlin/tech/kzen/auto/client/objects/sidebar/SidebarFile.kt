package tech.kzen.auto.client.objects.sidebar

import kotlinx.css.*
import kotlinx.css.properties.TextDecorationLine
import kotlinx.css.properties.textDecoration
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import org.w3c.dom.HTMLButtonElement
import react.*
import styled.css
import styled.styledA
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.structure.notation.edit.DeleteBundleCommand


class SidebarFile(
        props: Props
):
        RComponent<SidebarFile.Props, SidebarFile.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var bundlePath: BundlePath
    ): RProps


    // TODO: centralize menu logic with SidebarFolder / ActionController
    class State(
            var hoverItem: Boolean,
            var hoverOptions: Boolean,
            var optionsOpen: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    private var buttonRef: HTMLButtonElement? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun SidebarFile.State.init(props: SidebarFile.Props) {
        optionsOpen = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onMouseOver(itemOrMenu: Boolean) {
        if (itemOrMenu) {
            setState {
                hoverItem = true
            }
        }
        else {
            setState {
                hoverOptions = true
            }
        }
    }


    private fun onMouseOut(itemOrMenu: Boolean) {
        if (itemOrMenu) {
            setState {
                hoverItem = false
            }
        }
        else {
            setState {
                hoverOptions = false
            }
        }
    }


    private fun onOptionsToggle() {
        setState {
            optionsOpen = ! optionsOpen
        }
    }


    private fun onOptionsClose() {
        setState {
            optionsOpen = false
            hoverItem = false
            hoverOptions = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun displayPath(bundlePath: BundlePath): String {
        val path = bundlePath.segments.subList(1, bundlePath.segments.size - 1)
        val suffix = bundlePath.segments.last()

        val parts = path.plus(suffix.substringBeforeLast("."))

        return parts.joinToString("/")
    }


    private fun onRemove() {
        onOptionsClose()

        async {
            ClientContext.commandBus.apply(DeleteBundleCommand(props.bundlePath))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val indent = 2.em

        styledDiv {
            css {
                position = Position.relative
                height = 2.em
                width = 100.pct.minus(indent)
                marginLeft = indent
            }

            attrs {
                onMouseOverFunction = {
                    onMouseOver(true)
                }

                onMouseOutFunction = {
                    onMouseOut(true)
                }
            }

//            val iconWidth = 22.px
            val iconWidth = 24.px

            styledDiv {
                css {
                    position = Position.absolute
//                    width = 100.pct
                    top = 0.px
                    left = 0.px

                    height = iconWidth
                }

                child(PlaylistPlayIcon::class) {
                    attrs {
                        title = "Script"
                    }
                }
            }

            styledDiv {
                css {
                    position = Position.absolute
//                    width = 100.pct
                    top = 0.px
                    left = iconWidth
                    width = 100.pct.minus(iconWidth)
                    marginLeft = 6.px

//                    fontSize = (1.2).em
                    marginTop = 2.px
//                    cursor = Cursor.pointer
                }


//                attrs {
//                    onClickFunction = {
//                        setState {
//                            this.bundlePath = bundlePath
//                        }
//                    }
//                }

                styledA {
                    css {
                        color = Color.inherit

//                        textDecoration = TextDecoration("inherit")
                        textDecoration(TextDecorationLine.initial)
                    }

                    attrs {
                        href = "#" + props.bundlePath.asString()
                    }

                    +displayPath(props.bundlePath)
                }
            }

            styledDiv {
                css {
                    position = Position.absolute
                    top = 0.px
                    right = 0.px

//                    backgroundColor = Color.blue
                }

                renderOptionsMenu()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderOptionsMenu() {
        styledSpan {
            css {
                // NB: blinks in and out without this
                backgroundColor = Color.transparent

                if (! (state.hoverItem || state.hoverOptions)) {
                    display = Display.none
                }
            }

            attrs {
                onMouseOverFunction = {
                    onMouseOver(false)
                }

                onMouseOutFunction = {
                    onMouseOut(false)
                }
            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Options..."
                    onClick = ::onOptionsToggle

                    buttonRef = {
                        this@SidebarFile.buttonRef = it
                    }

                    style = reactStyle {
                        marginTop = (-13).px
                        marginRight = (-16).px
                    }
                }

                child(MoreVertIcon::class) {}
            }
        }

        child(MaterialMenu::class) {
            attrs {
                open = state.optionsOpen

                onClose = ::onOptionsClose

                anchorEl = buttonRef
            }

            renderMenuItems()
        }
    }


    private fun RBuilder.renderMenuItems() {
        val iconStyle = reactStyle {
            marginRight = 1.em
        }

        child(MaterialMenuItem::class) {
            attrs {
//                onClick = ::onAdd
                onClick = ::onOptionsClose
            }
            child(EditIcon::class) {
                attrs {
                    style = iconStyle
                }
            }
            +"Rename"
        }

        child(MaterialMenuItem::class) {
            attrs {
                onClick = ::onRemove
//                onClick = ::onOptionsClose
            }
            child(DeleteIcon::class) {
                attrs {
                    style = iconStyle
                }
            }
            +"Remove"
        }
    }
}