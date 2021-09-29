package tech.kzen.auto.client.objects.document.pipeline.analysis

import kotlinx.css.*
import react.RBuilder
import react.RPureComponent
import react.dom.span
import styled.css
import styled.styledDiv
import styled.styledSpan


class AnalysisFlatController(
    props: Props
):
    RPureComponent<AnalysisFlatController.Props, AnalysisFlatController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var filteredColumnNames: Map<String, Boolean>?
//        var inputAndCalculatedColumns: HeaderListing?
    }


    interface State: react.State


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val filteredColumnNames = props.filteredColumnNames
            ?: return

        styledDiv {
            css {
                maxHeight = 10.em
                overflowY = Overflow.auto
            }

            +"Columns: "

            for ((index, e) in filteredColumnNames.entries.withIndex()) {
                span {
                    key = e.key

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

                        +"${index + 1}"

                        styledSpan {
                            css {
                                fontFamily = "monospace"
                                whiteSpace = WhiteSpace.nowrap
                                marginLeft = 1.em
                            }

                            +e.key

                            if (e.value) {
                                +"[+]"
                            }
                            else {
                                +"[-]"
                            }
                        }
                    }

                    +" "
                }
            }
        }
    }
}