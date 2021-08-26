package tech.kzen.auto.client.objects.document.report.pivot

import kotlinx.css.em
import kotlinx.css.fontSize
import kotlinx.css.marginBottom
import kotlinx.css.marginRight
import react.RBuilder
import react.RPureComponent
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.material.TableChartIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec


class ReportPivot(
    props: Props
):
    RPureComponent<ReportPivot.Props, react.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var reportState: ReportState,
        var dispatcher: ReportDispatcher
    ): react.Props


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val pivotSpec = props.reportState.analysisSpec().pivot

//        renderHeader()

        if (props.reportState.columnListing == null) {
            // TODO: is this good usability?
            return
        }

        styledDiv {
            css {
//                marginBottom = 0.5.em
                marginBottom = 1.em
            }
            renderRows(pivotSpec)
        }

        renderValues(pivotSpec)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeader() {
        styledDiv {
            child(TableChartIcon::class) {
                attrs {
                    style = reactStyle {
                        fontSize = 1.75.em
                        marginRight = 0.25.em
                    }
                }
            }

            styledSpan {
                css {
                    fontSize = 2.em
                }

                +"Pivot"
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderRows(pivotSpec: PivotSpec) {
        child(PivotRowList::class) {
            attrs {
                this.pivotSpec = pivotSpec
                reportState = props.reportState
                dispatcher = props.dispatcher
            }
        }
    }


    private fun RBuilder.renderValues(pivotSpec: PivotSpec) {
        child(PivotValueList::class) {
            attrs {
                this.pivotSpec = pivotSpec
                reportState = props.reportState
                dispatcher = props.dispatcher
            }
        }
    }
}