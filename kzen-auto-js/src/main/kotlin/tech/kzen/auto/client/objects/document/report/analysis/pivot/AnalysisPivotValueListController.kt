package tech.kzen.auto.client.objects.document.report.analysis.pivot

import web.cssom.LineStyle
import web.cssom.em
import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import react.react
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.analysis.model.ReportAnalysisStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec


//---------------------------------------------------------------------------------------------------------------------
external interface AnalysisPivotValueListControllerProps: react.Props {
    var spec: PivotSpec
    var inputAndCalculatedColumns: HeaderListing?
    var analysisStore: ReportAnalysisStore
    var runningOrLoading: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class AnalysisPivotValueListController(
    props: AnalysisPivotValueListControllerProps
):
    RPureComponent<AnalysisPivotValueListControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                borderTopWidth = ReportController.separatorWidth
                borderTopColor = ReportController.separatorColor
                borderTopStyle = LineStyle.solid
                paddingTop = 0.5.em
            }

            span {
                css {
                    fontSize = 1.5.em
                }
                +"Values"
            }

            table {
                tbody {
                    for (e in props.spec.values.columns) {
                        tr {
                            key = e.key

                            td {
                                css {
                                    paddingLeft = 0.5.em
                                }

                                AnalysisPivotValueItemController::class.react {
                                    columnName = e.key
                                    analysisStore = props.analysisStore
                                    runningOrLoading = props.runningOrLoading
                                }
                            }

                            td {
                                AnalysisPivotValueTypeController::class.react {
                                    columnName = e.key
                                    pivotValueSpec = e.value
                                    analysisStore = props.analysisStore
                                }
                            }
                        }
                    }
                }
            }

            renderAdd()
        }
    }


    private fun ChildrenBuilder.renderAdd() {
        AnalysisPivotValueAddController::class.react {
            spec = props.spec
            inputAndCalculatedColumns = props.inputAndCalculatedColumns
            analysisStore = props.analysisStore
            runningOrLoading = props.runningOrLoading
        }
    }
}