package tech.kzen.auto.client.objects.document.report.analysis

import csstype.*
import emotion.react.css
import js.core.jso
import mui.material.Size
import mui.material.ToggleButton
import mui.material.ToggleButtonGroup
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.react
import tech.kzen.auto.client.objects.document.report.analysis.model.ReportAnalysisStore
import tech.kzen.auto.client.objects.document.report.analysis.pivot.AnalysisPivotController
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.objects.document.report.output.model.ReportOutputStore
import tech.kzen.auto.client.objects.document.report.widget.ReportBottomEgress
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.iconify
import tech.kzen.auto.client.wrap.iconify.vaadinIconLayout
import tech.kzen.auto.client.wrap.iconify.vaadinIconTable
import tech.kzen.auto.client.wrap.material.TableChartIcon
import tech.kzen.auto.common.objects.document.report.listing.AnalysisColumnInfo
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisType


//---------------------------------------------------------------------------------------------------------------------
external interface ReportAnalysisControllerProps: react.Props {
    var spec: AnalysisSpec
    var inputAndCalculatedColumns: HeaderListing?
    var analysisColumnInfo: AnalysisColumnInfo?
    var runningOrLoading: Boolean
    var analysisStore: ReportAnalysisStore
    var inputStore: ReportInputStore
    var outputStore: ReportOutputStore
}


//---------------------------------------------------------------------------------------------------------------------
class ReportAnalysisController(
    props: ReportAnalysisControllerProps
):
    RPureComponent<ReportAnalysisControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private fun onTypeChange(analysisType: AnalysisType) {
        props.analysisStore.setAnalysisTypeAsync(analysisType)
    }


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
        renderAnalysis()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderHeader() {
        val editDisabled = props.runningOrLoading

        div {
            span {
                css {
                    height = 2.em
                    width = 2.5.em
                    position = Position.relative
                }

                TableChartIcon::class.react {
                    style = jso {
                        position = Position.absolute
                        fontSize = 2.5.em
                        top = (-14.5).px
                        left = (-5).px
                    }
                }
            }

            span {
                css {
                    marginLeft = 1.25.em
                    fontSize = 2.em
                }

                +"Analysis"
            }

            span {
                css {
                    float = Float.right
                }

                ToggleButtonGroup {
                    value = props.spec.type.name
                    exclusive = true
                    onChange = { _, v ->
                        onTypeChange(AnalysisType.valueOf(v as String))
                    }

                    ToggleButton {
                        value = AnalysisType.FlatData.name
                        disabled = editDisabled
                        size = Size.medium
                        css {
                            height = 34.px
                            color = NamedColor.black
                            borderWidth = 2.px
                        }

                        span {
                            css {
                                fontSize = 1.5.em
                                marginRight = 0.25.em
                                marginBottom = (-0.25).em
                            }
                            iconify(vaadinIconTable)
                        }

                        +"Flat Data"
                    }

                    ToggleButton {
                        value = AnalysisType.PivotTable.name
                        disabled = editDisabled
                        size = Size.medium
                        css {
                            height = 34.px
                            color = NamedColor.black
                            borderWidth = 2.px
                        }

                        span {
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
    private fun ChildrenBuilder.renderAnalysis() {
        div {
            when (props.spec.type) {
                AnalysisType.FlatData ->
                    renderFlat()

                AnalysisType.PivotTable ->
                    renderPivot()
            }
        }
    }


    private fun ChildrenBuilder.renderFlat() {
        AnalysisFlatController::class.react {
            mainLocation = props.analysisStore.mainLocation()
            analysisColumnInfo = props.analysisColumnInfo
            spec = props.spec.flat
            reportInputStore = props.inputStore
            reportOutputStore = props.outputStore
            runningOrLoading = props.runningOrLoading
        }
    }


    private fun ChildrenBuilder.renderPivot() {
        AnalysisPivotController::class.react {
            spec = props.spec.pivot
            inputAndCalculatedColumns = props.inputAndCalculatedColumns
            analysisStore = props.analysisStore
            runningOrLoading = props.runningOrLoading
        }
    }
}