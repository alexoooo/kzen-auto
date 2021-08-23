package tech.kzen.auto.client.objects.document.pipeline.analysis

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import react.dom.span
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing


class AnalysisFlatController(
    props: Props
):
    RPureComponent<AnalysisFlatController.Props, AnalysisFlatController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var inputAndCalculatedColumns: HeaderListing?
//        var runningOrLoading: Boolean
    }


    interface State: RState {
//        var analysisType: AnalysisType
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val header = props.inputAndCalculatedColumns
            ?: return

        styledDiv {
            css {
                maxHeight = 10.em
                overflowY = Overflow.auto
            }

            +"Columns: "

            for (i in header.values.withIndex()) {
                span {
                    key = i.value

                    styledDiv {
                        css {
                            display = Display.inlineBlock
                            whiteSpace = WhiteSpace.nowrap
                            borderStyle = BorderStyle.solid
                            borderWidth = 1.px
                            borderColor = Color.lightGray
                            marginLeft = 0.5.em
                            marginRight = 0.5.em
                            marginTop = 0.25.em
                            marginBottom = 0.25.em
                            padding(0.25.em)
                        }

                        +"${i.index + 1}"

                        styledSpan {
                            css {
                                fontFamily = "monospace"
                                whiteSpace = WhiteSpace.nowrap
                                marginLeft = 1.em
                            }

                            +i.value
                        }
                    }

                    +" "
                }
            }
        }
    }
}