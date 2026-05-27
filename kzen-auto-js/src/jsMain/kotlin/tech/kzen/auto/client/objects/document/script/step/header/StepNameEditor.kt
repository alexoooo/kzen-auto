package tech.kzen.auto.client.objects.document.script.step.header

import emotion.react.css
import mui.material.IconButton
import mui.material.Size
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.edit.ObjectNameEditor
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.iconByName
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.service.notation.NotationConventions
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface StepNameEditorProps: react.Props {
    var objectLocation: ObjectLocation
    var description: String
    var title: String
}


external interface StepNameEditorState: react.State {
    var editing: Boolean
    var nameHovered: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
// NB: extracted as a pure component so that toggling the parent's nameHovered state doesn't cascade into the
//     MUI IconButton subtree (~10 non-pure fibers — ButtonBase, ripple, SvgIcon, ownerState forwarding).
//     Props must be stable references (use a class field for onAction, not ::method).
external interface RenameButtonProps: react.Props {
    var onAction: () -> Unit
}


class RenameButton(props: RenameButtonProps):
    RPureComponent<RenameButtonProps, react.State>(props)
{
    override fun ChildrenBuilder.render() {
        IconButton {
            title = "Rename"
            size = Size.small

            onClick = { props.onAction() }

            iconByName("Edit") {}
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
class StepNameEditor(
    props: StepNameEditorProps
):
    RPureComponent<StepNameEditorProps, StepNameEditorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun title(graphStructure: GraphStructure, objectLocation: ObjectLocation): String {
            val titleAttributeText = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, AutoConventions.titleAttributePath)
                ?.asString()

            return titleAttributeText
                ?: graphStructure.graphNotation.getString(
                    objectLocation, NotationConventions.isAttributePath)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun StepNameEditorState.init(props: StepNameEditorProps) {
        editing = false
        nameHovered = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    // NB: stable reference — RenameButton (RPureComponent) bails out only if its onAction prop is referentially
    //     equal across renders. ::onStartEdit would create a fresh bound reference per access.
    private val onStartEditCallback: () -> Unit = { onStartEdit() }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onStartEdit() {
        if (!state.editing) {
            setState {
                editing = true
            }
        }
    }


    private fun onCloseEdit() {
        setState {
            editing = false
        }
    }


    private fun onNameAreaEnter() {
        if (!state.nameHovered) {
            setState {
                nameHovered = true
            }
        }
    }


    private fun onNameAreaLeave() {
        if (state.nameHovered) {
            setState {
                nameHovered = false
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                minWidth = 0.px
            }

            onMouseEnter = { onNameAreaEnter() }
            onMouseLeave = { onNameAreaLeave() }

            if (state.editing) {
                renderEditor()
            }
            else {
                renderReader()
            }
        }
    }


    private fun ChildrenBuilder.renderReader() {
        span {
            css {
                fontSize = 1.5.em
                fontWeight = FontWeight.bold
                whiteSpace = WhiteSpace.nowrap
                overflow = Overflow.hidden
                textOverflow = TextOverflow.ellipsis
                minWidth = 0.px
            }

            title = props.description

            val objectName = props.objectLocation.objectPath.name

            if (AutoConventions.isAnonymous(objectName)) {
                +props.title
            }
            else {
                +objectName.value
            }
        }

        // NB: the wrapper div carries the hover-driven opacity. Placing it on a plain element (not the IconButton's
        //     sx) means re-rendering on hover doesn't cascade into MUI's IconButton subtree.
        div {
            css {
                display = Display.inlineFlex
                alignItems = AlignItems.center
                marginLeft = 0.25.em
                opacity = if (state.nameHovered) number(1.0) else number(0.0)
            }

            RenameButton::class.react {
                onAction = onStartEditCallback
            }
        }
    }


    private fun ChildrenBuilder.renderEditor() {
        div {
            css {
                flexGrow = number(1.0)
                minWidth = 0.px
            }

            ObjectNameEditor::class.react {
                objectLocation = props.objectLocation
                onClose = ::onCloseEdit
            }
        }
    }
}
