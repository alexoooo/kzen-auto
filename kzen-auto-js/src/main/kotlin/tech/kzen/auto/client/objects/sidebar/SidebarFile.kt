package tech.kzen.auto.client.objects.sidebar

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import org.w3c.dom.HTMLButtonElement
import react.*
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.AttributePath
import tech.kzen.lib.common.api.model.DocumentPath
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.edit.DeleteDocumentCommand
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation


class SidebarFile(
        props: Props
):
        RComponent<SidebarFile.Props, SidebarFile.State>(props)
{
    companion object {
        val iconAttribute = AttributePath.ofAttribute(AttributeName("icon"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var structure: GraphStructure,
            var documentPath: DocumentPath,
            var selected: Boolean
    ): RProps


    // TODO: centralize menu logic with SidebarFolder / ActionController
    class State(
            var hoverItem: Boolean,
            var hoverOptions: Boolean,
            var optionsOpen: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    private var buttonRef: HTMLButtonElement? = null
    private var nameEditorRef: DocumentNameEditor? = null


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
    private fun onRename() {
        onOptionsClose()
        nameEditorRef?.onEdit()
    }


    private fun onRemove() {
        onOptionsClose()

        async {
            ClientContext.commandBus.apply(DeleteDocumentCommand(props.documentPath))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        val documentArchetype = props.structure.graphNotation

        val archetypeLocation = DocumentArchetype
                .archetypeLocation(props.structure.graphNotation, props.documentPath)
                ?: return

        val icon = (props.structure.graphNotation.coalesce
                .get(archetypeLocation)
                .get(iconAttribute) as ScalarAttributeNotation
                ).value

        val indent = 2.em
        val iconWidth = 22.px

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

            styledDiv {
                css {
                    position = Position.absolute
//                    width = 100.pct
                    top = 0.px
                    left = 0.px

                    height = iconWidth
                }

                child(iconClassForName(icon)) {
                    attrs {
                        title = archetypeLocation.objectPath.name.value
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

                    marginTop = 2.px

                    if (props.selected) {
                        fontWeight = FontWeight.bold
                    }
                }

                child(DocumentNameEditor::class) {
                    attrs {
                        ref<DocumentNameEditor> {
//                            console.log("^^^^^ BundleNameEditor ref", it)
                            nameEditorRef = it
                        }

                        this.documentPath = props.documentPath
                    }
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
                onClick = ::onRename
//                onClick = ::onOptionsClose
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