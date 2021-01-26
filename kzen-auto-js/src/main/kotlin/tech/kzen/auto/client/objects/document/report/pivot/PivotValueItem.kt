package tech.kzen.auto.client.objects.document.report.pivot

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.report.state.PivotValueRemoveRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.DeleteIcon
import tech.kzen.auto.client.wrap.MaterialIconButton
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.spec.PivotSpec
import tech.kzen.auto.common.objects.document.report.spec.PivotValueColumnSpec


class PivotValueItem(
    props: Props
):
    RPureComponent<PivotValueItem.Props, PivotValueItem.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var columnName: String,
        var pivotValueSpec: PivotValueColumnSpec,

        var pivotSpec: PivotSpec,
        var reportState: ReportState,
        var dispatcher: ReportDispatcher
    ): RProps


    class State(
//        var hover: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        hover = false
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private fun onMouseOver() {
//        setState {
//            hover = true
//        }
//    }
//
//
//    private fun onMouseOut() {
//        setState {
//            hover = false
//        }
//    }


    private fun onDelete() {
        props.dispatcher.dispatchAsync(
            PivotValueRemoveRequest(props.columnName))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
//            attrs {
//                onMouseOverFunction = {
//                    onMouseOver()
//                }
//
//                onMouseOutFunction = {
//                    onMouseOut()
//                }
//            }

            css {
                width = 100.pct
                paddingTop = 0.25.em
                paddingBottom = 0.25.em
            }

            +props.columnName

            child(MaterialIconButton::class) {
                attrs {
                    style = reactStyle {
                        float = Float.right
                        marginTop = (-0.6).em
                        marginBottom = (-0.6).em
                        marginLeft = 0.25.em

//                        if (! state.hover) {
//                            visibility = Visibility.hidden
//                        }
                    }

                    onClick = {
                        onDelete()
                    }

                    disabled =
                        props.reportState.isInitiating() ||
                        props.reportState.taskRunning ||
                        props.reportState.pivotLoading
                }

                child(DeleteIcon::class) {}
            }
        }
    }
}