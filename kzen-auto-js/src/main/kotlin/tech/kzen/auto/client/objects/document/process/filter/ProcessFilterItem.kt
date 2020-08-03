package tech.kzen.auto.client.objects.document.process.filter

import kotlinx.css.*
import react.*
import react.dom.span
import react.dom.tbody
import react.dom.tr
import styled.*
import tech.kzen.auto.client.objects.document.process.state.FilterRemoveError
import tech.kzen.auto.client.objects.document.process.state.FilterRemoveRequest
import tech.kzen.auto.client.objects.document.process.state.ProcessDispatcher
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.DeleteIcon
import tech.kzen.auto.client.wrap.ExpandLessIcon
import tech.kzen.auto.client.wrap.ExpandMoreIcon
import tech.kzen.auto.client.wrap.MaterialIconButton
import tech.kzen.auto.common.objects.document.filter.CriteriaSpec


class ProcessFilterItem(
    props: Props
):
    RPureComponent<ProcessFilterItem.Props, ProcessFilterItem.State>(props) {
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var processState: ProcessState,
        var dispatcher: ProcessDispatcher,
        var criteriaSpec: CriteriaSpec,
        var columnName: String
    ) : RProps


    class State(
        var open: Boolean,
        var removeError: String?
    ) : RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        open = false
        removeError = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onDelete() {
        async {
            val effect = props.dispatcher.dispatch(
                FilterRemoveRequest(props.columnName)
            ).single()

            if (effect is FilterRemoveError) {
                setState {
                    removeError = effect.message
                }
            }
        }
    }


    private fun onOpenToggle() {
        setState {
            open = ! open
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
                borderTopWidth = 1.px
                borderTopStyle = BorderStyle.solid
                borderTopColor = Color.lightGray
            }

            renderHeader()

            if (state.open) {
                renderBody()
            }
        }
    }


    private fun RBuilder.renderHeader(/*valueSummary: ColumnSummary?*/) {
        styledTable {
            css {
                width = 100.pct
            }

            tbody {
                tr {
                    styledTd {
                        css {
                            width = 100.pct.minus(20.em)
                        }

                        styledSpan {
                            css {
                                fontSize = 1.5.em
                            }

                            +props.columnName
                        }
                    }

                    styledTd {
                        css {
                            width = 20.em
                            textAlign = TextAlign.right
                        }

                        span {
                            val removeError = state.removeError
                            if (removeError != null) {
                                +removeError
                            }

                            child(MaterialIconButton::class) {
                                attrs {
                                    onClick = {
                                        onDelete()
                                    }
                                }

                                child(DeleteIcon::class) {}
                            }
                        }

                        child(MaterialIconButton::class) {
                            attrs {
                                onClick = {
                                    onOpenToggle()
                                }

//                                disabled = (props.columnSummary?.isEmpty() ?: true)
                            }

                            if (state.open) {
                                child(ExpandLessIcon::class) {}
                            }
                            else {
                                child(ExpandMoreIcon::class) {}
                            }
                        }
                    }
                }
            }
        }
    }


    private fun RBuilder.renderBody(/*valueSummary: ColumnSummary?*/) {
        +"${props.criteriaSpec.columnRequiredValues[props.columnName]}"
    }
}