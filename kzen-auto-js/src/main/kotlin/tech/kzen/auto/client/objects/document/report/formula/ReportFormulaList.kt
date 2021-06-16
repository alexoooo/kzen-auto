package tech.kzen.auto.client.objects.document.report.formula

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.report.edge.ReportBottomEgress
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.material.FunctionsIcon
import tech.kzen.auto.client.wrap.material.MaterialCircularProgress
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec


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

            child(ReportBottomEgress::class) {
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
            styledSpan {
                css {
                    height = 2.em
                    width = 2.5.em
                    position = Position.relative
                }

                child(FunctionsIcon::class) {
                    attrs {
                        style = reactStyle {
                            position = Position.absolute
                            fontSize = 2.5.em
                            top = (-16.5).px
                            left = (-3.5).px
                        }
                    }
                }
            }
//            styledDiv {
//                css {
//                    display = Display.inlineBlock
//                    height = 2.em
//                    width = 2.em
//                    position = Position.relative
//                    marginRight = 0.25.em
//                }
//                child(FunctionsIcon::class) {
//                    attrs {
//                        style = reactStyle {
//                            position = Position.absolute
//                            top = 0.px
//                            left = 0.px
//                            fontSize = 2.25.em
//                        }
//                    }
//                }
//            }

            styledSpan {
                css {
                    marginLeft = 1.25.em
                    fontSize = 2.em
                }

//                +"Calculated Columns"
                +"Formulas"
            }

            styledSpan {
                css {
                    float = Float.right
                }

                if (props.reportState.formulaLoading) {
                    child(MaterialCircularProgress::class) {}
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderCalculatedColumns() {
        val formulaSpec = props.reportState.formulaSpec()

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