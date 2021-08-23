package tech.kzen.auto.client.objects.document.pipeline.analysis.pivot

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.pipeline.analysis.model.PipelineAnalysisStore
import tech.kzen.auto.client.wrap.material.DeleteIcon
import tech.kzen.auto.client.wrap.material.MaterialIconButton
import tech.kzen.auto.client.wrap.reactStyle


class AnalysisPivotValueItemController(
    props: Props
):
    RPureComponent<AnalysisPivotValueItemController.Props, AnalysisPivotValueItemController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var columnName: String
        var analysisStore: PipelineAnalysisStore
        var runningOrLoading: Boolean
    }


    interface State: RState {
//        var hover: Boolean
    }


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
        props.analysisStore.removeValueAsync(props.columnName)
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
                    size = "small"

                    style = reactStyle {
                        float = Float.right
                        marginTop = (-0.25).em
                        marginBottom = (-0.25).em
                        marginLeft = 0.25.em
                    }

                    onClick = {
                        onDelete()
                    }

                    disabled = props.runningOrLoading
                }

                child(DeleteIcon::class) {}
            }
        }
    }
}