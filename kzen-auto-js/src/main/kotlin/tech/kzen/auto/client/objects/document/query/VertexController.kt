package tech.kzen.auto.client.objects.document.query

import kotlinx.css.Float
import kotlinx.css.em
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.dom.div
import styled.css
import styled.styledH3
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.query.QueryDocument
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.RemoveObjectInAttributeCommand


class VertexController(
        props: Props
):
        RComponent<VertexController.Props, VertexController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        private val filePathAttribute = AttributePath.parse("filePath")
//    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var attributeNesting: AttributeNesting,
            var objectLocation: ObjectLocation,
            var graphStructure: GraphStructure
    ): RProps


    class State(
//            var value: String,
//            var submitDebounce: FunctionWithDebounce,
//            var pending: Boolean,
//
//            var executionResult: ExecutionResult?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        val filePath = props
//                .graphStructure
//                .graphNotation
//                .transitiveAttribute(props.objectLocation, filePathAttribute)
//                ?.asString()
//                ?: ""
//
//        value = filePath
//
//        submitDebounce = lodash.debounce({
//            editParameterCommandAsync()
//        }, 1000)
//
//        pending = false
//
//        executionResult = null
    }


    override fun componentDidMount() {
//        async {
//            executeAction()
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private fun editParameterCommandAsync() {
//        async {
//            editParameterCommand()
//        }
//    }


//    private suspend fun editParameterCommand() {
//        ClientContext.commandBus.apply(UpsertAttributeCommand(
//                props.objectLocation,
//                filePathAttribute.attribute,
//                ScalarAttributeNotation(state.value)))
//
//        setState {
//            pending = false
//        }
//
//        executeAction()
//    }


//    private suspend fun executeAction() {
//        val executionResult = ClientContext.restClient.performDetached(props.objectLocation)
//        setState {
//            this.executionResult = executionResult
//        }
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRemove() {
        async {
            val sourceMain = ObjectLocation(
                    props.objectLocation.documentPath,
                    NotationConventions.mainObjectPath)

            val objectAttributePath = AttributePath(
                    QueryDocument.verticesAttributePath.attribute,
                    props.attributeNesting)

            ClientContext.commandBus.apply(RemoveObjectInAttributeCommand(
                    sourceMain, objectAttributePath))
        }
    }


//    private fun onValueChange(newValue: String) {
//        setState {
//            value = newValue
//            pending = true
//        }
//
//        state.submitDebounce.apply()
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        child(MaterialCard::class) {
//            attrs {
//                style = reactStyles
//            }

            child(MaterialCardContent::class) {
                div {
                    child(MaterialIconButton::class) {
                        attrs {
                            title = "Remove"

                            style = reactStyle {
                                float = Float.right
                                marginTop = (-1).em
                            }

                            onClick = ::onRemove
                        }

                        child(DeleteIcon::class) {}
                    }

                    styledH3 {
                        css {
                            width = 10.em
                        }

//                        child(TripOriginIcon::class) {
                        child(SettingsInputComponentIcon::class) {
                            attrs {
                                style = reactStyle {
                                    marginRight = (0.5).em
                                }
                            }
                        }
//                        +"CSV source"
                        +"Vertex"
                    }
                }

//                child(MaterialTextField::class) {
//                    attrs {
//                        fullWidth = true
//
//                        label = "File Path"
//                        value = state.value
//
//                        onChange = {
//                            val target = it.target as HTMLInputElement
//                            onValueChange(target.value)
//                        }
//                    }
//                }
//
//                state.executionResult?.let {
//                    renderResult(it)
//                }
            }
        }
    }


//    private fun RBuilder.renderResult(executionResult: ExecutionResult) {
//        styledDiv {
//            css {
//                marginTop = 1.em
//            }
//
//            when (executionResult) {
//                is ExecutionError -> {
//                    styledDiv {
//                        css {
//                            color = Color.red
//                        }
//                        +executionResult.errorMessage
//                    }
//                }
//
//                is ExecutionSuccess -> {
//                    +executionResult.value.get().toString()
//                }
//            }
//        }
//    }
}