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
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.edit.DeleteDocumentCommand
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation
import kotlin.js.Date


class SidebarFile(
        props: Props
):
        RPureComponent<SidebarFile.Props, SidebarFile.State>(props)
{
    companion object {
        val iconAttribute = AttributePath.ofName(AttributeName("icon"))
        private const val menuDanglingTimeout = 300

        private val iconWidth = 22.px
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
            var optionsOpen: Boolean,
            var editing: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    private var buttonRef: HTMLButtonElement? = null
    private var nameEditorRef: DocumentNameEditor? = null

    private var processingOption: Boolean = false
    private var optionCompletedTime: Double? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        optionsOpen = false
        editing = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onMouseOver(itemOrMenu: Boolean) {
        if (state.optionsOpen || processingOption) {
//            console.log("^^^ onMouseOver hoverItem - skip due to optionsOpen")
            return
        }

        optionCompletedTime?.let {
            val now = Date.now()
            val elapsed = now - it
//            console.log("^^^ onMouseOver hoverItem - elapsed", elapsed)

            if (elapsed < menuDanglingTimeout) {
                return
            }
            else {
                optionCompletedTime = null
            }
        }

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


    private fun onOptionsOpen() {
        setState {
            optionsOpen = true
        }
    }


    private fun onOptionsClose() {
        setState {
            optionsOpen = false
            hoverItem = false
            hoverOptions = false
        }
    }


    private fun onOptionsCancel() {
//        console.log("^^^^^^ onOptionsCancel")
        onOptionsClose()
        optionCompletedTime = Date.now()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRename() {
        performOption {
            nameEditorRef?.onEdit()
        }
    }


    private fun onRemove() {
        performOption {
            ClientContext.commandBus.apply(DeleteDocumentCommand(props.documentPath))
        }
    }


    private fun performOption(action: suspend () -> Unit) {
        processingOption = true
        onOptionsClose()

        async {
            action.invoke()
        }.then {
            optionCompletedTime = Date.now()
            processingOption = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        val documentArchetype = props.structure.graphNotation

        val archetypeLocation = DocumentArchetype
                .archetypeLocation(props.structure.graphNotation, props.documentPath)
                ?: return

        styledDiv {
            css {
                position = Position.relative
                height = 2.em
                width = 100.pct.minus(SidebarFolder.indent)
                marginLeft = SidebarFolder.indent

//                backgroundColor = Color.red
            }

            attrs {
                onMouseOverFunction = {
                    onMouseOver(true)
                }

                onMouseOutFunction = {
                    onMouseOut(true)
                }
            }

            if (state.editing) {
                renderIconAndName(archetypeLocation)
            }
            else {
                styledA {
                    css {
                        color = Color.inherit
                        textDecoration(TextDecorationLine.initial)
                        width = 100.pct
                        height = 100.pct
                    }

                    attrs {
                        href = "#" + props.documentPath.asString()
                    }

                    renderIconAndName(archetypeLocation)
                }
            }

            styledDiv {
                css {
                    position = Position.absolute
                    top = 0.px
                    right = 0.px
                }

                renderOptionsMenu()
            }
        }
    }


    private fun RBuilder.renderIconAndName(
            archetypeLocation: ObjectLocation
    ) {
        val icon = (props.structure.graphNotation.coalesce[archetypeLocation]!!
                .get(iconAttribute) as ScalarAttributeNotation
                ).value

        styledDiv {
            css {
                position = Position.absolute
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
                top = 0.px
                left = iconWidth
                width = 100.pct.minus(iconWidth)
                marginLeft = 6.px

                if (props.selected) {
                    fontWeight = FontWeight.bold
                }

                height = 2.em
            }

            child(DocumentNameEditor::class) {
                attrs {
                    ref<DocumentNameEditor> {
                        nameEditorRef = it
                    }

                    this.documentPath = props.documentPath

                    initialEditing = state.editing

                    onEditing = {
                        setState {
                            editing = it
                        }
                    }
                }
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
                    onClick = ::onOptionsOpen

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

                onClose = ::onOptionsCancel

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
            }
            child(DeleteIcon::class) {
                attrs {
                    style = iconStyle
                }
            }
            +"Delete"
        }
    }
}