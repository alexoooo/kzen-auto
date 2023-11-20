package tech.kzen.auto.client.objects.document.data

import emotion.react.css
import mui.material.IconButton
import mui.material.Size
import mui.material.TextField
import react.*
import react.dom.html.ReactHTML.div
import react.dom.onChange
import tech.kzen.auto.client.objects.ProjectController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.data.spec.FieldFormatListSpec
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.MirroredGraphError
import web.cssom.Display
import web.cssom.NamedColor
import web.cssom.VerticalAlign
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface DataFormatFieldAddProps: Props {
    var objectLocation: ObjectLocation
}


external interface DataFormatFieldAddState: State {
    var newFieldName: String
    var adding: Boolean
    var previousError: String?
}


//---------------------------------------------------------------------------------------------------------------------
class DataFormatFieldAdd(
    props: DataFormatFieldAddProps
):
    RPureComponent<DataFormatFieldAddProps, DataFormatFieldAddState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun DataFormatFieldAddState.init(props: DataFormatFieldAddProps) {
        newFieldName = ""
        adding = false
        previousError = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onFieldNameEdit(value: String) {
        setState {
            newFieldName = value
        }
    }


    private fun onFieldNameEnter(event: react.dom.events.KeyboardEvent<*>) {
        ClientInputUtils.handleEnter(event) {
            onAdd()
        }
    }


    private fun onAdd() {
        val fieldName = state.newFieldName
        if (fieldName.isBlank()) {
            return
        }

        setState {
            adding = true
            previousError = null
        }

        async {
//            delay(1)
            addSync(fieldName)
        }
    }


    private suspend fun addSync(fieldName: String) {
        val command = FieldFormatListSpec.addCommand(props.objectLocation, fieldName)

        val result = ClientContext.mirroredGraphStore.apply(
            command, ProjectController.suppressErrorDisplay)

        val error = (result as? MirroredGraphError)
            ?.error
            ?.let { it.message ?: "$result" }

        setState {
            adding = false
            previousError = error

            if (error == null) {
                newFieldName = ""
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                display = Display.tableRow
            }

            renderSubmitButton()
            renderNameEditor()
        }

        renderErrorIfPresent()
    }


    private fun ChildrenBuilder.renderSubmitButton() {
        div {
            css {
                display = Display.tableCell
                verticalAlign = VerticalAlign.middle
            }
            IconButton {
                title = "Add new field to Data Format"
                disabled = state.adding

                onClick = {
                    onAdd()
                }

                AddCircleOutlineIcon::class.react {}
            }
        }
    }


    private fun ChildrenBuilder.renderNameEditor() {
        div {
            css {
                display = Display.tableCell
            }

            TextField {
                size = Size.small
                label = ReactNode("New field name")
                value = state.newFieldName

                onChange = {
                    val value = (it.target as HTMLInputElement).value
                    onFieldNameEdit(value)
                }

                onKeyDown = ::onFieldNameEnter
            }
        }
    }


    private fun ChildrenBuilder.renderErrorIfPresent() {
        val previousError = state.previousError
            ?: return

        div {
            css {
                color = NamedColor.red
            }
            +"Error: $previousError"
        }
    }
}