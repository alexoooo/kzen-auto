package tech.kzen.auto.client.objects.document.report.analysis

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.report.edge.ReportBottomEgress
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.iconify.iconify
import tech.kzen.auto.client.wrap.iconify.vaadinIconLayout
import tech.kzen.auto.client.wrap.iconify.vaadinIconTable
import tech.kzen.auto.client.wrap.material.MaterialToggleButton
import tech.kzen.auto.client.wrap.material.MaterialToggleButtonGroup
import tech.kzen.auto.client.wrap.material.TableChartIcon
import tech.kzen.auto.client.wrap.reactStyle


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
        var pivot: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        pivot = false
    }


    private fun onTypeChange(pivot: Boolean) {
        setState {
            this.pivot = pivot
        }
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
                        value = state.pivot.toString()
                        exclusive = true
                        onChange = { _, v ->
                            if (v is String) {
                                onTypeChange(v.toBooleanStrict())
                            }
                        }
                    }

                    child(MaterialToggleButton::class) {
                        attrs {
                            value = "false"
//                            disabled = editDisabled
                            size = "small"
                            style = reactStyle {
                                height = 2.5.em
                                color = Color.black
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
                            value = "true"
//                            disabled = editDisabled
                            size = "small"
                            style = reactStyle {
                                height = 2.5.em
                                color = Color.black
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
        styledDiv {
            +"[Analysis]"
        }
    }
}