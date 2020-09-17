package tech.kzen.auto.client.objects.document.process.filter

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.graph.edge.BottomEgress
import tech.kzen.auto.client.objects.document.graph.edge.TopIngress
import tech.kzen.auto.client.objects.document.process.state.ProcessDispatcher
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.client.wrap.FilterListIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.process.CriteriaSpec
import tech.kzen.auto.common.objects.document.process.FilterConventions
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition


class ProcessFilterList(
    props: Props
):
    RPureComponent<ProcessFilterList.Props, ProcessFilterList.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var processState: ProcessState,
        var dispatcher: ProcessDispatcher
    ): RProps


    class State(
//        var adding: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        adding = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                position = Position.relative
                filter = "drop-shadow(0 1px 1px gray)"
                height = 100.pct
                marginTop = 5.px
            }

            child(TopIngress::class) {
                attrs {
                    ingressColor = Color.white
                    parentWidth = 100.pct
                }
            }

            styledDiv {
                css {
                    borderRadius = 3.px
                    backgroundColor = Color.white
                    width = 100.pct
                }

                styledDiv {
                    css {
                        padding(0.5.em)
                    }

                    renderContent()
                }
            }

            child(BottomEgress::class) {
                attrs {
                    this.egressColor = Color.white
                    parentWidth = 100.pct
                }
            }
        }
    }


    private fun RBuilder.renderContent() {
        renderHeader()
        renderFilters()
    }


    private fun RBuilder.renderHeader() {
        styledDiv {
            child(FilterListIcon::class) {
                attrs {
                    style = reactStyle {
                        fontSize = 1.75.em
                        marginRight = 0.25.em
                    }
                }
            }

            styledSpan {
                css {
                    fontSize = 2.em
                }

                +"Filter"
            }
        }
    }


    private fun RBuilder.renderFilters() {
        val criteriaDefinition = props
            .processState
            .clientState
            .graphDefinitionAttempt
            .successful
            .objectDefinitions[props.processState.mainLocation]!!
            .attributeDefinitions[FilterConventions.criteriaAttributeName]!!
        val criteriaSpec = (criteriaDefinition as ValueAttributeDefinition).value as CriteriaSpec
//        console.log("^^^^^^^^^ !!! criteriaSpec: $criteriaSpec")

        styledDiv {
            renderFilterList(criteriaSpec)
            renderFilterAdd(criteriaSpec)
        }
    }


    private fun RBuilder.renderFilterList(criteriaSpec: CriteriaSpec) {
        styledDiv {
            for ((index, columnName) in criteriaSpec.columnRequiredValues.keys.withIndex()) {
                styledDiv {
                    key = columnName

                    if (index < criteriaSpec.columnRequiredValues.size - 1) {
                        css {
                            marginBottom = 1.em
                        }
                    }

                    child(ProcessFilterItem::class) {
                        attrs {
                            processState = props.processState
                            dispatcher = props.dispatcher
                            this.criteriaSpec = criteriaSpec
                            this.columnName = columnName
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderFilterAdd(criteriaSpec: CriteriaSpec) {
        child(ProcessFilterAdd::class) {
            attrs {
                processState = props.processState
                dispatcher = props.dispatcher
                this.criteriaSpec = criteriaSpec
            }
        }
    }
}