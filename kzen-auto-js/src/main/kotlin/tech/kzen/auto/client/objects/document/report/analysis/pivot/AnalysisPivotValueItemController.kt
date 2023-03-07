package tech.kzen.auto.client.objects.document.report.analysis.pivot
//
//import kotlinx.css.*
//import react.RBuilder
//import react.RPureComponent
//import react.State
//import styled.css
//import styled.styledDiv
//import tech.kzen.auto.client.objects.document.report.analysis.model.ReportAnalysisStore
//import tech.kzen.auto.client.wrap.material.DeleteIcon
//import tech.kzen.auto.client.wrap.material.MaterialIconButton
//import tech.kzen.auto.client.wrap.reactStyle
//
//
////---------------------------------------------------------------------------------------------------------------------
//external interface AnalysisPivotValueItemControllerProps: react.Props {
//    var columnName: String
//    var analysisStore: ReportAnalysisStore
//    var runningOrLoading: Boolean
//}
//
//
////---------------------------------------------------------------------------------------------------------------------
//class AnalysisPivotValueItemController(
//    props: AnalysisPivotValueItemControllerProps
//):
//    RPureComponent<AnalysisPivotValueItemControllerProps, State>(props)
//{
//    //-----------------------------------------------------------------------------------------------------------------
////    private fun onMouseOver() {
////        setState {
////            hover = true
////        }
////    }
////
////
////    private fun onMouseOut() {
////        setState {
////            hover = false
////        }
////    }
//
//
//    private fun onDelete() {
//        props.analysisStore.removeValueAsync(props.columnName)
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun RBuilder.render() {
//        styledDiv {
////            attrs {
////                onMouseOverFunction = {
////                    onMouseOver()
////                }
////
////                onMouseOutFunction = {
////                    onMouseOut()
////                }
////            }
//
//            css {
//                width = 100.pct
//                paddingTop = 0.25.em
//                paddingBottom = 0.25.em
//            }
//
//            +props.columnName
//
//            child(MaterialIconButton::class) {
//                attrs {
//                    size = "small"
//
//                    style = reactStyle {
//                        float = Float.right
//                        marginTop = (-0.25).em
//                        marginBottom = (-0.25).em
//                        marginLeft = 0.25.em
//                    }
//
//                    onClick = {
//                        onDelete()
//                    }
//
//                    disabled = props.runningOrLoading
//                }
//
//                child(DeleteIcon::class) {}
//            }
//        }
//    }
//}