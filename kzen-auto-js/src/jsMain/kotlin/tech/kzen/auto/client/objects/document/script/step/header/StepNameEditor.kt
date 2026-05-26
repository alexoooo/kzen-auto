package tech.kzen.auto.client.objects.document.script.step.header

import emotion.react.css
import mui.material.IconButton
import mui.material.Size
import mui.system.sx
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

        IconButton {
            title = "Rename"
            size = Size.small

            sx {
                marginLeft = 0.25.em
                opacity = if (state.nameHovered) number(1.0) else number(0.0)
            }

            onClick = { onStartEdit() }

            iconByName("Edit") {}
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
