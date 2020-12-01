package tech.kzen.auto.client.objects.document.report.pivot

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.graph.edge.BottomEgress
import tech.kzen.auto.client.objects.document.graph.edge.TopIngress
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.TableChartIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.spec.PivotSpec
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition


class ReportPivot(
    props: Props
):
    RPureComponent<ReportPivot.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var reportState: ReportState,
        var dispatcher: ReportDispatcher
    ): RProps


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                position = Position.relative
                filter = "drop-shadow(0 1px 1px gray)"
                height = 100.pct
                marginTop = 5.px
            }

            child(TopIngress::class) {
                attrs {
                    ingressColor = Color.white
                    parentWidth = 100.pct
                }
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

            child(BottomEgress::class) {
                attrs {
                    this.egressColor = Color.white
                    parentWidth = 100.pct
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderContent() {
        val pivotDefinition = props
            .reportState
            .clientState
            .graphDefinitionAttempt
            .successful
            .objectDefinitions[props.reportState.mainLocation]!!
            .attributeDefinitions[ReportConventions.pivotAttributeName]!!

        val pivotSpec = (pivotDefinition as ValueAttributeDefinition).value as PivotSpec

        renderHeader()

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