package tech.kzen.auto.client.ui

import kotlinx.css.*
import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.style
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import react.*
import react.dom.h2
import react.dom.input
import react.dom.span
import styled.css
import styled.styledDiv
import styled.styledH2
import styled.styledSpan
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.exec.ExecutionStatus
import tech.kzen.lib.common.edit.RenameObjectCommand
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.ProjectNotation


@Suppress("unused")
class NameEditor(
        props: NameEditor.Props
) :
        RComponent<NameEditor.Props, NameEditor.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var objectName: String
    ) : RProps


    class State(
            var editing: Boolean,
            var value: String
    ) : RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun NameEditor.State.init(props: NameEditor.Props) {
//        console.log("ParameterEditor | State.init - ${props.name}")
        value = props.objectName

//        submitDebounce = lodash.debounce({
//            editParameterCommandAsync()
//        }, 1000)

        editing = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onNameChange(newValue: String) {
        setState {
            value = newValue
        }
    }


    private fun onCancel() {
        setState {
            editing = false
        }
    }


    private fun onRename() {
        async {
            ClientContext.commandBus.apply(RenameObjectCommand(
                    props.objectName, state.value))
        }
    }


    private fun onEdit() {
        setState {
            editing = true
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        if (state.editing) {

            styledDiv {
                css {
                    display = Display.inlineBlock
                    width = LinearDimension("calc(100% - 4em)")
                }

                child(MaterialTextField::class) {
                    attrs {
                        label = "Name"
                        fullWidth = true

                        value = state.value

                        onChange = {
                            val target = it.target as HTMLInputElement
                            onNameChange(target.value)
                        }
                    }
                }
            }

//            input(type = InputType.text) {
//                attrs {
//                    value = state.value
//
//                    onChangeFunction = {
//                        val target = it.target as HTMLInputElement
//                        onNameChange(target.value)
//                    }
//                }
//            }

            styledDiv {
                css {
//                    display = Display.inlineBlock
//                    width = 3.em
                    float = kotlinx.css.Float.right
                }

                child(MaterialIconButton::class) {
                    attrs {
                        style = reactStyle {
                            width = 1.5.em
                        }

                        onClick = ::onCancel
                    }

                    child(CancelIcon::class) {}
                }

                child(MaterialIconButton::class) {
                    attrs {
                        style = reactStyle {
                            width = 1.5.em
                            marginRight = (-0.5).em
                        }

                        onClick = ::onRename
                    }

                    child(SaveIcon::class) {}
                }
            }
        }
        else {
            styledH2 {
                css {
                    marginTop = 9.px
                    marginBottom = 10.px
                    cursor = Cursor.pointer
                }

                attrs {
                    title = "Edit name"

                    onClickFunction = {
                        onEdit()
                    }
                }

                +props.objectName
            }
        }
    }
}