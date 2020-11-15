package tech.kzen.auto.client.objects.document.process.pivot

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.*
import tech.kzen.auto.client.objects.document.process.state.ProcessDispatcher
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.common.objects.document.process.PivotSpec


class PivotValueList(
    props: Props
):
    RPureComponent<PivotValueList.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var pivotSpec: PivotSpec,
        var processState: ProcessState,
        var dispatcher: ProcessDispatcher
    ): RProps


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                borderTopWidth = 1.px
                borderTopStyle = BorderStyle.solid
                borderTopColor = Color.lightGray
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
                                        processState = props.processState
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
                                        processState = props.processState
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
                processState = props.processState
                dispatcher = props.dispatcher
            }
        }
    }
}