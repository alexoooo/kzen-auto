package tech.kzen.auto.client.objects.document.report.analysis
//
//import kotlinx.css.*
//import react.RBuilder
//import react.RPureComponent
//import react.State
//import styled.css
//import styled.styledDiv
//import styled.styledSpan
//import tech.kzen.auto.client.objects.document.report.analysis.model.ReportAnalysisStore
//import tech.kzen.auto.client.objects.document.report.analysis.pivot.AnalysisPivotController
//import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
//import tech.kzen.auto.client.objects.document.report.output.model.ReportOutputStore
//import tech.kzen.auto.client.objects.document.report.widget.ReportBottomEgress
//import tech.kzen.auto.client.wrap.iconify.iconify
//import tech.kzen.auto.client.wrap.iconify.vaadinIconLayout
//import tech.kzen.auto.client.wrap.iconify.vaadinIconTable
//import tech.kzen.auto.client.wrap.material.MaterialToggleButton
//import tech.kzen.auto.client.wrap.material.MaterialToggleButtonGroup
//import tech.kzen.auto.client.wrap.material.TableChartIcon
//import tech.kzen.auto.client.wrap.reactStyle
//import tech.kzen.auto.common.objects.document.report.listing.AnalysisColumnInfo
//import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
//import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
//import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisType
//
//
////---------------------------------------------------------------------------------------------------------------------
//external interface ReportAnalysisControllerProps: react.Props {
//    var spec: AnalysisSpec
//    var inputAndCalculatedColumns: HeaderListing?
//    var analysisColumnInfo: AnalysisColumnInfo?
//    var runningOrLoading: Boolean
//    var analysisStore: ReportAnalysisStore
//    var inputStore: ReportInputStore
//    var outputStore: ReportOutputStore
//}
//
//
////---------------------------------------------------------------------------------------------------------------------
//class ReportAnalysisController(
//    props: ReportAnalysisControllerProps
//):
//    RPureComponent<ReportAnalysisControllerProps, State>(props)
//{
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun onTypeChange(analysisType: AnalysisType) {
//        props.analysisStore.setAnalysisTypeAsync(analysisType)
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun RBuilder.render() {
//        styledDiv {
//            css {
//                position = Position.relative
//                filter = "drop-shadow(0 1px 1px gray)"
//                height = 100.pct
//                marginTop = 5.px
//            }
//
//            styledDiv {
//                css {
//                    borderRadius = 3.px
//                    backgroundColor = Color.white
//                    width = 100.pct
//                }
//
//                styledDiv {
//                    css {
//                        padding(0.5.em)
//                    }
//
//                    renderContent()
//                }
//            }
//
//            child(ReportBottomEgress::class) {
//                attrs {
//                    this.egressColor = Color.white
//                    parentWidth = 100.pct
//                }
//            }
//        }
//    }
//
//
//
//    private fun RBuilder.renderContent() {
//        renderHeader()
//        renderAnalysis()
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun RBuilder.renderHeader() {
//        val editDisabled = props.runningOrLoading
//
//        styledDiv {
//            styledSpan {
//                css {
//                    height = 2.em
//                    width = 2.5.em
//                    position = Position.relative
//                }
//
//                child(TableChartIcon::class) {
//                    attrs {
//                        style = reactStyle {
//                            position = Position.absolute
//                            fontSize = 2.5.em
//                            top = (-14.5).px
//                            left = (-5).px
//                        }
//                    }
//                }
//            }
//
//            styledSpan {
//                css {
//                    marginLeft = 1.25.em
//                    fontSize = 2.em
//                }
//
//                +"Analysis"
//            }
//
//            styledSpan {
//                css {
//                    float = Float.right
//                }
//
//                child(MaterialToggleButtonGroup::class) {
//                    attrs {
//                        value = props.spec.type.name
//                        exclusive = true
//                        onChange = { _, v ->
//                            if (v is String) {
//                                onTypeChange(AnalysisType.valueOf(v))
//                            }
//                        }
//                    }
//
//                    child(MaterialToggleButton::class) {
//                        attrs {
//                            value = AnalysisType.FlatData.name
//                            disabled = editDisabled
//                            size = "medium"
//                            style = reactStyle {
//                                height = 34.px
//                                color = Color.black
//                                borderWidth = 2.px
//                            }
//                        }
//
//                        styledSpan {
//                            css {
//                                fontSize = 1.5.em
//                                marginRight = 0.25.em
//                                marginBottom = (-0.25).em
//                            }
//                            iconify(vaadinIconTable)
//                        }
//
//                        +"Flat Data"
//                    }
//
//                    child(MaterialToggleButton::class) {
//                        attrs {
//                            value = AnalysisType.PivotTable.name
//                            disabled = editDisabled
//                            size = "medium"
//                            style = reactStyle {
//                                height = 34.px
//                                color = Color.black
//                                borderWidth = 2.px
//                            }
//                        }
//
//                        styledSpan {
//                            css {
//                                fontSize = 1.5.em
//                                marginRight = 0.25.em
//                                marginBottom = (-0.25).em
//                            }
//                            iconify(vaadinIconLayout)
//                        }
//
//                        +"Pivot Table"
//                    }
//                }
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun RBuilder.renderAnalysis() {
//        styledDiv {
//            when (props.spec.type) {
//                AnalysisType.FlatData ->
//                    renderFlat()
//
//                AnalysisType.PivotTable ->
//                    renderPivot()
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderFlat() {
//        child(AnalysisFlatController::class) {
//            attrs {
//                mainLocation = props.analysisStore.mainLocation()
//                analysisColumnInfo = props.analysisColumnInfo
//                spec = props.spec.flat
//                reportInputStore = props.inputStore
//                reportOutputStore = props.outputStore
//                runningOrLoading = props.runningOrLoading
//            }
//        }
//    }
//
//
//    private fun RBuilder.renderPivot() {
//        child(AnalysisPivotController::class) {
//            attrs {
//                spec = props.spec.pivot
//                inputAndCalculatedColumns = props.inputAndCalculatedColumns
//                analysisStore = props.analysisStore
//                runningOrLoading = props.runningOrLoading
//            }
//        }
//    }
//}