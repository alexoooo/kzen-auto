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
}


//---------------------------------------------------------------------------------------------------------------------
// NB: kept as its own pure component so the (~10 non-pure) MUI IconButton subtree — ButtonBase, ripple, SvgIcon,
//     ownerState forwarding — bails out when the editor re-renders for unrelated reasons.
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


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            // NB: the rename button is revealed on hover via pure CSS (see [data-rename-button] below) rather than
            //     a hover state field — a state toggle here would re-reconcile this step's sibling slots on every
            //     mouse move and flash them in React DevTools' "Highlight updates" overlay (a false positive).
            css {
                display = Display.flex
                alignItems = AlignItems.center
                minWidth = 0.px

                "&:hover [data-rename-button]" {
                    opacity = number(1.0)
                }
            }

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

        // NB: hidden by default; the enclosing name area's &:hover rule reveals it (data-rename-button hook).
        div {
            asDynamic()["data-rename-button"] = ""

            css {
                display = Display.inlineFlex
                alignItems = AlignItems.center
                marginLeft = 0.25.em
                opacity = number(0.0)
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
