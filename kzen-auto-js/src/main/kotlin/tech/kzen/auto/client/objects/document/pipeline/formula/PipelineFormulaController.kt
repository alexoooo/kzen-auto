package tech.kzen.auto.client.objects.document.pipeline.formula

import kotlinx.css.*
import react.RBuilder
import react.RPureComponent
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.pipeline.formula.model.PipelineFormulaState
import tech.kzen.auto.client.objects.document.pipeline.formula.model.PipelineFormulaStore
import tech.kzen.auto.client.objects.document.report.edge.ReportBottomEgress
import tech.kzen.auto.client.wrap.material.FunctionsIcon
import tech.kzen.auto.client.wrap.material.MaterialCircularProgress
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec


class PipelineFormulaController(
    props: Props
):
    RPureComponent<PipelineFormulaController.Props, PipelineFormulaController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var formulaSpec: FormulaSpec
        var formulaState: PipelineFormulaState
        var columnListing: List<String>?
        var runningOrLoading: Boolean
        var formulaStore: PipelineFormulaStore

//        var dispatcher: ReportDispatcher
    }


    interface State: react.State


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

            styledSpan {
                css {
                    marginLeft = 1.25.em
                    fontSize = 2.em
                }

                +"Formulas"
            }

            styledSpan {
                css {
                    float = Float.right
                }

                if (props.formulaState.formulaLoading) {
                    child(MaterialCircularProgress::class) {}
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderCalculatedColumns() {
        styledDiv {
            renderFormulaList()
            renderFormulaAdd()
        }
    }


    private fun RBuilder.renderFormulaList() {
        val formulas = props.formulaSpec.formulas

        styledDiv {
            for ((index, columnName) in formulas.keys.withIndex()) {
                styledDiv {
                    key = columnName

                    if (index < formulas.size - 1) {
                        css {
                            marginBottom = 1.em
                        }
                    }

                    child(FormulaItemController::class) {
                        attrs {
                            formulaState = props.formulaState
                            formulaSpec = props.formulaSpec
                            runningOrLoading = props.runningOrLoading
                            this.columnName = columnName
                            columnListing = props.columnListing
                            formulaStore = props.formulaStore
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderFormulaAdd() {
        child(FormulaAddController::class) {
            attrs {
                formulaSpec = props.formulaSpec
                formulaState = props.formulaState
                columnListing = props.columnListing
                runningOrLoading = props.runningOrLoading
                formulaStore = props.formulaStore
            }
        }
    }
}