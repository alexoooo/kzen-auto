package tech.kzen.auto.client.objects.document.pipeline.analysis.pivot

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.*
import tech.kzen.auto.client.objects.document.pipeline.PipelineController
import tech.kzen.auto.client.objects.document.pipeline.analysis.model.PipelineAnalysisStore
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec


class AnalysisPivotValueListController(
    props: Props
):
    RPureComponent<AnalysisPivotValueListController.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var spec: PivotSpec
        var inputAndCalculatedColumns: HeaderListing?
        var analysisStore: PipelineAnalysisStore
        var runningOrLoading: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                borderTopWidth = PipelineController.separatorWidth
                borderTopColor = PipelineController.separatorColor
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