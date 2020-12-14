package tech.kzen.auto.client.objects.document.report.formula

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
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.FunctionsIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition


class ReportFormulaList(
    props: Props
):
    RPureComponent<ReportFormulaList.Props, ReportFormulaList.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var reportState: ReportState,
        var dispatcher: ReportDispatcher
    ): RProps


    class State(
//        var adding: Boolean
    ): RState


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
        renderCalculatedColumns()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeader() {
        styledDiv {
            child(FunctionsIcon::class) {
                attrs {
                    style = reactStyle {
//                        fontSize = 1.75.em
//                        fontSize = 2.em
                        fontSize = 2.25.em
                        marginRight = 0.25.em
//                        marginTop = (-0.25).em
                        marginTop = 0.em
                    }
                }
            }

            styledSpan {
                css {
                    fontSize = 2.em
                }

                +"Calculated Columns"
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderCalculatedColumns() {
        val formulaDefinition = props
            .reportState
            .clientState
            .graphDefinitionAttempt
            .successful
            .objectDefinitions[props.reportState.mainLocation]!!
            .attributeDefinitions[ReportConventions.formulaAttributeName]!!
        val formulaSpec = (formulaDefinition as ValueAttributeDefinition).value as FormulaSpec

        styledDiv {
            renderFormulaList(formulaSpec)
            renderFormulaAdd(formulaSpec)
        }
    }


    private fun RBuilder.renderFormulaList(formulaSpec: FormulaSpec) {
        styledDiv {
            for ((index, columnName) in formulaSpec.formulas.keys.withIndex()) {
                styledDiv {
                    key = columnName

                    if (index < formulaSpec.formulas.size - 1) {
                        css {
                            marginBottom = 1.em
                        }
                    }

                    child(ReportFormulaItem::class) {
                        attrs {
                            reportState = props.reportState
                            dispatcher = props.dispatcher
                            this.formulaSpec = formulaSpec
                            this.columnName = columnName
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderFormulaAdd(formulaSpec: FormulaSpec) {
        child(ReportFormulaAdd::class) {
            attrs {
                reportState = props.reportState
                dispatcher = props.dispatcher
                this.formulaSpec = formulaSpec
            }
        }
    }
}