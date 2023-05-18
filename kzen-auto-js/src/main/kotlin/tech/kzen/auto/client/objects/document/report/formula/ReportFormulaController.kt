package tech.kzen.auto.client.objects.document.report.formula

import emotion.react.css
import js.core.jso
import mui.material.CircularProgress
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.react
import tech.kzen.auto.client.objects.document.report.formula.model.ReportFormulaState
import tech.kzen.auto.client.objects.document.report.formula.model.ReportFormulaStore
import tech.kzen.auto.client.objects.document.report.widget.ReportBottomEgress
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.FunctionsIcon
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ReportFormulaControllerProps: react.Props {
    var formulaSpec: FormulaSpec
    var formulaState: ReportFormulaState
    var inputColumns: List<String>?
    var runningOrLoading: Boolean
    var formulaStore: ReportFormulaStore
}


//---------------------------------------------------------------------------------------------------------------------
class ReportFormulaController(
    props: ReportFormulaControllerProps
):
    RPureComponent<ReportFormulaControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                position = Position.relative
                filter = dropShadow(0.px, 1.px, 1.px, NamedColor.gray)
                height = 100.pct
                marginTop = 5.px
            }

            div {
                css {
                    borderRadius = 3.px
                    backgroundColor = NamedColor.white
                    width = 100.pct
                }

                div {
                    css {
                        padding = Padding(0.5.em, 0.5.em, 0.5.em, 0.5.em)
                    }

                    renderContent()
                }
            }

            ReportBottomEgress::class.react {
                this.egressColor = NamedColor.white
                parentWidth = 100.pct
            }
        }
    }


    private fun ChildrenBuilder.renderContent() {
        renderHeader()
        renderCalculatedColumns()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderHeader() {
        div {
            span {
                css {
                    height = 2.em
                    width = 2.5.em
                    position = Position.relative
                }

                FunctionsIcon::class.react {
                    style = jso {
                        position = Position.absolute
                        fontSize = 2.5.em
                        top = (-16.5).px
                        left = (-3.5).px
                    }
                }
            }

            span {
                css {
                    marginLeft = 1.25.em
                    fontSize = 2.em
                }

                +"Formulas"
            }

            span {
                css {
                    float = Float.right
                }

                if (props.formulaState.formulaLoading) {
                    CircularProgress {}
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderCalculatedColumns() {
        div {
            renderFormulaList()
            renderFormulaAdd()
        }
    }


    private fun ChildrenBuilder.renderFormulaList() {
        val formulas = props.formulaSpec.formulas

        div {
            for ((index, columnName) in formulas.keys.withIndex()) {
                div {
                    key = columnName

                    if (index < formulas.size - 1) {
                        css {
                            marginBottom = 1.em
                        }
                    }

                    FormulaItemController::class.react {
                        formulaState = props.formulaState
                        formulaSpec = props.formulaSpec
                        runningOrLoading = props.runningOrLoading
                        this.columnName = columnName
                        inputColumns = props.inputColumns
                        formulaStore = props.formulaStore
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderFormulaAdd() {
        FormulaAddController::class.react {
            formulaSpec = props.formulaSpec
            formulaState = props.formulaState
            inputColumns = props.inputColumns
            runningOrLoading = props.runningOrLoading
            formulaStore = props.formulaStore
        }
    }
}