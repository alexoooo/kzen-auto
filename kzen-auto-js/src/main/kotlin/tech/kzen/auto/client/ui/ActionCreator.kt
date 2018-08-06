package tech.kzen.auto.client.ui

import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLInputElement
import react.*
import react.dom.*
import tech.kzen.auto.client.service.AutoContext
import tech.kzen.auto.client.service.RestClient
import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.edit.AddObjectCommand
import tech.kzen.lib.common.notation.model.ParameterConventions
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ProjectPath


@Suppress("unused")
class ActionCreator(
        props: ActionCreator.Props
) : RComponent<ActionCreator.Props, ActionCreator.State>(props) {

    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var notation: ProjectNotation,
            var path: ProjectPath
    ) : RProps


    class State(
            var name: String,
            var type: String
    ) : RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        console.log("ParameterEditor | State.init - ${props.name}")
        name = "new-action-name"

        val types = actionTypes()
        if (types.isEmpty()) {
            throw IllegalStateException("Must provide at least one action type")
        }
        type = types[0]
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onNameChange(newValue: String) {
        setState {
            name = newValue
        }
    }

    private fun onTypeChange(newValue: String) {
        setState {
            type = newValue
        }
    }


    private fun onSubmit() {
//        console.log("ParameterEditor.onSubmit")

        async {
            AutoContext.commandBus.apply(AddObjectCommand.ofParent(
                    props.path,
                    state.name,
                    state.type))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        fieldSet {
            legend {
                +"Add New Action"
            }


            div {
                +"Name: "
                input (type = InputType.text) {
                    attrs {
                        value = state.name

                        onChangeFunction = {
                            val target = it.target as HTMLInputElement
                            onNameChange(target.value)
                        }
                    }
                }
            }


            div {
                +"Type:"

                br {}
                select {
                    attrs {
                        value = state.type
                        onChangeFunction = {
                            val value: String = it.target!!.asDynamic().value as? String
                                    ?: throw IllegalStateException("Archetype name string expected")

                            onTypeChange(value)
                        }

                        // TODO: why is this necessary (or error otherwise)
                        multiple = true
                    }

                    for (actionType in actionTypes()) {
                        option {
                            attrs {
                                value = actionType
                                onChangeFunction = {
                                    console.log("#!#@! option onChangeFunction", it.currentTarget)
                                }
                            }

                            +actionType
                        }
                    }
                }
            }


            div {
                input (type = InputType.button) {
                    attrs {
                        value = "Add"
                        onClickFunction = {
                            onSubmit()
                        }
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun actionTypes(): List<String> {
        val actionTypes = mutableListOf<String>()

        for (e in props.notation.coalesce) {
            val isParameter = e.value.parameters[ParameterConventions.isParameter]
                    ?.asString()
                    ?: continue

            if (isParameter != "Action") {
                continue
            }

            actionTypes.add(e.key)
        }

        return actionTypes
    }
}
