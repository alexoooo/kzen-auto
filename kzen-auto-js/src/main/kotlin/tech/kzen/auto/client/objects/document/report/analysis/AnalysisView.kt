package tech.kzen.auto.client.objects.document.report.analysis

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import react.dom.span
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.report.edge.ReportBottomEgress
import tech.kzen.auto.client.objects.document.report.pivot.ReportPivot
import tech.kzen.auto.client.objects.document.report.state.AnalysisChangeTypeRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.iconify.iconify
import tech.kzen.auto.client.wrap.iconify.vaadinIconLayout
import tech.kzen.auto.client.wrap.iconify.vaadinIconTable
import tech.kzen.auto.client.wrap.material.MaterialToggleButton
import tech.kzen.auto.client.wrap.material.MaterialToggleButtonGroup
import tech.kzen.auto.client.wrap.material.TableChartIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisType


class AnalysisView(
    props: Props
):
    RPureComponent<AnalysisView.Props, AnalysisView.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
    }


    interface State: RState {
//        var analysisType: AnalysisType
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        analysisType = props.reportState.analysisSpec().type
    }


    private fun onTypeChange(analysisType: AnalysisType) {
        props.dispatcher.dispatchAsync(AnalysisChangeTypeRequest(analysisType))
//        setState {
//            this.pivot = pivot
//        }
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
        renderAnalysis()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeader() {
//        val showRefresh = props.reportState.isTaskRunning()

        styledDiv {
            styledSpan {
                css {
                    height = 2.em
                    width = 2.5.em
                    position = Position.relative
                }

                child(TableChartIcon::class) {
                    attrs {
                        style = reactStyle {
                            position = Position.absolute
                            fontSize = 2.5.em
                            top = (-14.5).px
                            left = (-5).px
                        }
                    }
                }
            }

            styledSpan {
                css {
                    marginLeft = 1.25.em
                    fontSize = 2.em
                }

                +"Analysis"
            }

            styledSpan {
                css {
                    float = Float.right
                }

                child(MaterialToggleButtonGroup::class) {
                    attrs {
                        value = props.reportState.analysisSpec().type.name
                        exclusive = true
                        onChange = { _, v ->
                            if (v is String) {
                                onTypeChange(AnalysisType.valueOf(v))
                            }
                        }
                    }

                    child(MaterialToggleButton::class) {
                        attrs {
                            value = AnalysisType.FlatData.name
//                            disabled = editDisabled
                            size = "medium"
                            style = reactStyle {
                                height = 34.px
                                color = Color.black
                                borderWidth = 2.px
                            }
                        }

                        styledSpan {
                            css {
                                fontSize = 1.5.em
                                marginRight = 0.25.em
                                marginBottom = (-0.25).em
                            }
                            iconify(vaadinIconTable)
                        }

                        +"Flat Data"
                    }

                    child(MaterialToggleButton::class) {
                        attrs {
                            value = AnalysisType.PivotTable.name
//                            disabled = editDisabled
                            size = "medium"
                            style = reactStyle {
                                height = 34.px
                                color = Color.black
                                borderWidth = 2.px
                            }
                        }

                        styledSpan {
                            css {
                                fontSize = 1.5.em
                                marginRight = 0.25.em
                                marginBottom = (-0.25).em
                            }
                            iconify(vaadinIconLayout)
                        }

                        +"Pivot Table"
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderAnalysis() {
        val analysis = props.reportState.analysisSpec()

        styledDiv {
            when (analysis.type) {
                AnalysisType.FlatData ->
                    renderFlat()

                AnalysisType.PivotTable ->
                    renderPivot()
            }
        }
    }


    private fun RBuilder.renderFlat() {
        val header = props.reportState.inputAndCalculatedColumns()
            ?: return

        styledDiv {
            css {
                maxHeight = 10.em
                overflowY = Overflow.auto
            }

            +"Columns: "

            for (i in header.values.withIndex()) {
                span {
                    key = i.value

                    styledDiv {
                        css {
                            display = Display.inlineBlock
                            whiteSpace = WhiteSpace.nowrap
                            borderStyle = BorderStyle.solid
                            borderWidth = 1.px
                            borderColor = Color.lightGray
                            marginLeft = 0.5.em
                            marginRight = 0.5.em
                            marginTop = 0.25.em
                            marginBottom = 0.25.em
                            padding(0.25.em)
                        }

                        +"${i.index + 1}"

                        styledSpan {
                            css {
                                fontFamily = "monospace"
                                whiteSpace = WhiteSpace.nowrap
                                marginLeft = 1.em
                            }

                            +i.value
                        }
                    }

                    +" "
                }
            }
        }
    }


    private fun RBuilder.renderPivot() {
        child(ReportPivot::class) {
            attrs {
                this.reportState = props.reportState
                this.dispatcher = props.dispatcher
            }
        }
    }
}