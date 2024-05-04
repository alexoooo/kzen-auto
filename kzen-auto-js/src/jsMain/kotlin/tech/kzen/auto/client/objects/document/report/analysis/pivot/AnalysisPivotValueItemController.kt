package tech.kzen.auto.client.objects.document.report.analysis.pivot

import emotion.react.css
import mui.material.IconButton
import mui.material.Size
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.react
import tech.kzen.auto.client.objects.document.report.analysis.model.ReportAnalysisStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.DeleteIcon
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import web.cssom.Float
import web.cssom.em
import web.cssom.pct


//---------------------------------------------------------------------------------------------------------------------
external interface AnalysisPivotValueItemControllerProps: react.Props {
    var columnName: HeaderLabel
    var analysisStore: ReportAnalysisStore
    var runningOrLoading: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class AnalysisPivotValueItemController(
    props: AnalysisPivotValueItemControllerProps
):
    RPureComponent<AnalysisPivotValueItemControllerProps, State>(props)
{
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
    override fun ChildrenBuilder.render() {
        div {
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

            +props.columnName.render()

            IconButton {
                size = Size.small

                css {
                    float = Float.right
                    marginTop = (-0.25).em
                    marginBottom = (-0.25).em
                    marginLeft = 0.25.em
                }

                onClick = {
                    onDelete()
                }

                disabled = props.runningOrLoading

                DeleteIcon::class.react {}
            }
        }
    }
}