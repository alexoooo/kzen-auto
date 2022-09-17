package tech.kzen.auto.client.objects.document.report.analysis.pivot

import kotlinx.css.*
import react.RBuilder
import react.RPureComponent
import styled.*
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.analysis.model.ReportAnalysisStore
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec


//---------------------------------------------------------------------------------------------------------------------
interface AnalysisPivotValueListControllerProps: react.Props {
    var spec: PivotSpec
    var inputAndCalculatedColumns: HeaderListing?
    var analysisStore: ReportAnalysisStore
    var runningOrLoading: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class AnalysisPivotValueListController(
    props: AnalysisPivotValueListControllerProps
):
    RPureComponent<AnalysisPivotValueListControllerProps, react.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                borderTopWidth = ReportController.separatorWidth
                borderTopColor = ReportController.separatorColor
                borderTopStyle = BorderStyle.solid
                paddingTop = 0.5.em
            }

            styledSpan {
                css {
                    fontSize = 1.5.em
                }
                +"Values"
            }

            styledTable {
                styledTbody {
                    for (e in props.spec.values.columns) {
                        styledTr {
                            key = e.key

                            styledTd {
                                css {
                                    paddingLeft = 0.5.em
                                }

                                child(AnalysisPivotValueItemController::class) {
                                    attrs {
                                        columnName = e.key
                                        analysisStore = props.analysisStore
                                        runningOrLoading = props.runningOrLoading
                                    }
                                }
                            }

                            styledTd {
                                child(AnalysisPivotValueTypeController::class) {
                                    attrs {
                                        columnName = e.key
                                        pivotValueSpec = e.value
                                        analysisStore = props.analysisStore
                                    }
                                }
                            }
                        }
                    }
                }
            }

            renderAdd()
        }
    }


    private fun RBuilder.renderAdd() {
        child(AnalysisPivotValueAddController::class) {
            attrs {
                spec = props.spec
                inputAndCalculatedColumns = props.inputAndCalculatedColumns
                analysisStore = props.analysisStore
                runningOrLoading = props.runningOrLoading
            }
        }
    }
}