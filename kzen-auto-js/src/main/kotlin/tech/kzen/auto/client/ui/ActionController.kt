package tech.kzen.auto.client.ui

import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.dom.div
import react.dom.input
import tech.kzen.auto.client.service.AutoExecutor
import tech.kzen.auto.client.service.AutoModelService
import tech.kzen.auto.common.api.AutoAction
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.metadata.model.ObjectMetadata
import tech.kzen.lib.common.notation.model.ObjectNotation
import tech.kzen.lib.common.notation.model.ParameterNotation
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ScalarParameterNotation


class ActionController(
        props: ActionController.Props
): RComponent<ActionController.Props, RState>(props) {
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var name: String,
            var notation: ProjectNotation,
            var metadata: GraphMetadata,
            var executor: AutoExecutor
    ) : RProps


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRun() {
        props.executor.run(props.name)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val objectMetadata = props.metadata.objectMetadata[props.name]!!
//        val objectNotation = props.notation.coalesce[props.name]!!

        div(classes = "actionController") {
            +"[ ${props.name} ]"

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

            input (type = InputType.button) {
                attrs {
                    value = "Run"
                    onClickFunction = { onRun() }
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