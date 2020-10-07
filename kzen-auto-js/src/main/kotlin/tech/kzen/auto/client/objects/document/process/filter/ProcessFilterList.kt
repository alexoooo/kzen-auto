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
import tech.kzen.auto.common.objects.document.process.FilterSpec
import tech.kzen.auto.common.objects.document.process.ProcessConventions
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


    //-----------------------------------------------------------------------------------------------------------------
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


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderFilters() {
        val filterDefinition = props
            .processState
            .clientState
            .graphDefinitionAttempt
            .successful
            .objectDefinitions[props.processState.mainLocation]!!
            .attributeDefinitions[ProcessConventions.filterAttributeName]!!
        val filterSpec = (filterDefinition as ValueAttributeDefinition).value as FilterSpec

        styledDiv {
            renderFilterList(filterSpec)
            renderFilterAdd(filterSpec)
        }
    }


    private fun RBuilder.renderFilterList(filterSpec: FilterSpec) {
        styledDiv {
            for ((index, columnName) in filterSpec.columns.keys.withIndex()) {
                styledDiv {
                    key = columnName

                    if (index < filterSpec.columns.size - 1) {
                        css {
                            marginBottom = 1.em
                        }
                    }

                    child(ProcessFilterItem::class) {
                        attrs {
                            processState = props.processState
                            dispatcher = props.dispatcher
                            this.filterSpec = filterSpec
                            this.columnName = columnName
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderFilterAdd(filterSpec: FilterSpec) {
        child(ProcessFilterAdd::class) {
            attrs {
                processState = props.processState
                dispatcher = props.dispatcher
                this.filterSpec = filterSpec
            }
        }
    }
}