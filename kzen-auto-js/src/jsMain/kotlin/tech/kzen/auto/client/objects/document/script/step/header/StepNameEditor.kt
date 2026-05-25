package tech.kzen.auto.client.objects.document.script.step.header

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.edit.ObjectNameEditor
import tech.kzen.auto.client.wrap.RPureComponent
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

    var editSignal: StepNameEditor.EditSignal
}


external interface StepNameEditorState: react.State {
    var editing: Boolean
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
    class EditSignal {
        private var callback: (() -> Unit)? = null

        fun trigger() {
            check(callback != null)
            callback!!.invoke()
        }

        fun attach(callback: () -> Unit) {
            check(this.callback == null)
            this.callback = callback
        }

        fun detach() {
            this.callback = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun StepNameEditorState.init(props: StepNameEditorProps) {
        editing = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        this.props.editSignal.attach(::onEdit)
    }


    override fun componentWillUnmount() {
        this.props.editSignal.detach()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onCancel() {
        setState {
            editing = false
        }
    }


    private fun onEdit() {
        if (! state.editing) {
            setState {
                editing = true
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                height = StepHeader.headerHeight
                width = 100.pct
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
        div {
            css {
                display = Display.inlineBlock
                cursor = Cursor.pointer
                height = StepHeader.headerHeight
                width = 100.pct

                marginTop = 10.px
            }

            title = props.description

            span {
                css {
                    width = 100.pct
                    height = StepHeader.headerHeight

                    fontSize = 1.5.em
                    fontWeight = FontWeight.bold
                }

                val objectName = props.objectLocation.objectPath.name

                if (AutoConventions.isAnonymous(objectName)) {
                    +props.title
                }
                else {
                    +objectName.value
                }
            }
        }
    }


    private fun ChildrenBuilder.renderEditor() {
        div {
            css {
                height = StepHeader.headerHeight
                marginTop = 8.px
            }

            ObjectNameEditor::class.react {
                objectLocation = props.objectLocation
                onClose = ::onCancel
            }
        }
    }
}
