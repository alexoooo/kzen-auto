package tech.kzen.auto.client.ui

import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLInputElement
import react.*
import react.dom.div
import react.dom.hr
import react.dom.input
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.edit.RemoveObjectCommand
import tech.kzen.lib.common.edit.RenameObjectCommand
import tech.kzen.lib.common.edit.ShiftObjectCommand
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.ParameterNotation
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ScalarParameterNotation


class ActionController(
        props: ActionController.Props
): RComponent<ActionController.Props, ActionController.State>(props) {
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var name: String,
            var notation: ProjectNotation,
            var metadata: GraphMetadata/*,
            var executor: AutoExecutor*/
    ) : RProps


    class State(
            var name: String
    ) : RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: ActionController.Props) {
        name = props.name
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRun() {
        async {
            ClientContext.restClient.performAction(props.name)
        }
//        props.executor.run(props.name)
    }


    private fun onNameChange(newValue: String) {
        setState {
            name = newValue
        }
    }


    private fun onRename() {
        async {
            ClientContext.commandBus.apply(RenameObjectCommand(
                    props.name, state.name))
        }
    }


    private fun onRemove() {
        async {
            ClientContext.commandBus.apply(RemoveObjectCommand(
                    props.name))
        }
    }


    private fun onShiftUp() {
        val packagePath = props.notation.findPackage(props.name)
        val packageNotation = props.notation.packages[packagePath]!!
        val index = packageNotation.indexOf(props.name)

        async {
            ClientContext.commandBus.apply(ShiftObjectCommand(
                    props.name, index - 1))
        }
    }


    private fun onShiftDown() {
        val packagePath = props.notation.findPackage(props.name)
        val packageNotation = props.notation.packages[packagePath]!!
        val index = packageNotation.indexOf(props.name)

        async {
            ClientContext.commandBus.apply(ShiftObjectCommand(
                    props.name, index + 1))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val objectMetadata = props.metadata.objectMetadata[props.name]!!
//        val objectNotation = props.notation.coalesce[props.name]!!

        div(classes = "actionController") {

            div {
                +("Name: ")

                input(type = InputType.text) {
                    attrs {
                        value = state.name

                        onChangeFunction = {
                            val target = it.target as HTMLInputElement
                            onNameChange(target.value)
                        }
                    }
                }

                input (type = InputType.button) {
                    attrs {
                        value = "Rename"
                        onClickFunction = {
                            onRename()
                        }
                    }
                }
            }

            hr {}

            for (e in objectMetadata.parameters) {
                val value =
                        props.notation.transitiveParameter(props.name, e.key)
                        ?: continue

                if (e.key == "is") {
                    continue
                }

                div(classes = "child") {
                    renderParameter(e.key, value)
                }
            }

            hr {}

            input (type = InputType.button) {
                attrs {
                    value = "Run"
                    onClickFunction = { onRun() }
                }
            }

            input (type = InputType.button) {
                attrs {
                    value = "Shift Up"
                    onClickFunction = {
                        onShiftUp()
                    }
                }
            }

            input (type = InputType.button) {
                attrs {
                    value = "Shift Down"
                    onClickFunction = {
                        onShiftDown()
                    }
                }
            }

            input (type = InputType.button) {
                attrs {
                    value = "Remove"
                    onClickFunction = {
                        onRemove()
                    }
                }
            }
        }
    }


    private fun RBuilder.renderParameter(
            parameterName: String,
            parameterValue: ParameterNotation
    ) {
        when (parameterValue) {
            is ScalarParameterNotation -> {
                val scalarValue = parameterValue.value

                when (scalarValue) {
                    is String ->
                        child(ParameterEditor::class) {
                            attrs {
                                objectName = props.name
                                parameterPath = parameterName
                                value = scalarValue
                            }
                        }

                    else ->
                        +"[[ ${parameterValue.value} ]]"
                }
            }

            else ->
                +"$parameterValue"
        }
    }
}