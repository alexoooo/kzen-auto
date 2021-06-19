package tech.kzen.auto.client.objects.document.report.pivot

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.*
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec


class PivotValueList(
    props: Props
):
    RPureComponent<PivotValueList.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var pivotSpec: PivotSpec,
        var reportState: ReportState,
        var dispatcher: ReportDispatcher
    ): RProps


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
                    for (e in props.pivotSpec.values.columns) {
                        styledTr {
                            key = e.key

                            styledTd {
                                css {
                                    paddingLeft = 0.5.em
                                }

                                child(PivotValueItem::class) {
                                    attrs {
                                        columnName = e.key
                                        pivotValueSpec = e.value

                                        pivotSpec = props.pivotSpec
                                        reportState = props.reportState
                                        dispatcher = props.dispatcher
                                    }
                                }
                            }

                            styledTd {
                                child(PivotValueTypes::class) {
                                    attrs {
                                        columnName = e.key
                                        pivotValueSpec = e.value

                                        pivotSpec = props.pivotSpec
                                        reportState = props.reportState
                                        dispatcher = props.dispatcher
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
        child(PivotValueAdd::class) {
            attrs {
                pivotSpec = props.pivotSpec
                reportState = props.reportState
                dispatcher = props.dispatcher
            }
        }
    }
}