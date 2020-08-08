package tech.kzen.auto.client.objects.document.process.filter

import kotlinx.css.*
import react.*
import react.dom.span
import react.dom.tbody
import react.dom.tr
import styled.*
import tech.kzen.auto.client.objects.document.common.AttributePathValueEditor
import tech.kzen.auto.client.objects.document.process.state.*
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.filter.CriteriaSpec
import tech.kzen.auto.common.objects.document.filter.FilterConventions
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.platform.collect.persistentListOf


class ProcessFilterItem(
    props: Props
):
    RPureComponent<ProcessFilterItem.Props, ProcessFilterItem.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var processState: ProcessState,
        var dispatcher: ProcessDispatcher,
        var criteriaSpec: CriteriaSpec,
        var columnName: String
    ): RProps


    class State(
        var open: Boolean,
        var removeError: String?
    ): RState


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


//    private fun onValuesChange(newValues: List<String>) {
//        props.dispatcher.dispatchAsync(FilterUpdateRequest(
//            props.columnName, newValues
//        ))
//    }



    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val requiredValues = props.criteriaSpec.columnRequiredValues[props.columnName]
            ?: return

        styledDiv {
            css {
                borderTopWidth = 1.px
                borderTopStyle = BorderStyle.solid
                borderTopColor = Color.lightGray
            }

            renderHeader()

            if (state.open || requiredValues.isNotEmpty()) {
                styledDiv {
                    css {
                        marginLeft = 1.em
                        marginRight = 1.em
                    }

                    if (state.open) {
                        renderDetail()
                    }
                    else {
                        renderSummary(requiredValues)
                    }
                }
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


    private fun RBuilder.renderSummary(requiredValues: Set<String>) {
        +requiredValues.joinToString {
            if (it.isBlank()) {
                "(blank)"
            }
            else {
                it
            }
        }
    }


    private fun RBuilder.renderDetail(/*valueSummary: ColumnSummary?*/) {
        child(AttributePathValueEditor::class) {
            attrs {
                labelOverride = "Filter values (one per line)"

                clientState = props.processState.clientState
                objectLocation = props.processState.mainLocation

                attributePath = AttributePath(
                    FilterConventions.criteriaAttributeName,
                    AttributeNesting(persistentListOf(AttributeSegment.ofKey(props.columnName))))

                valueType = TypeMetadata(
                    ClassNames.kotlinList,
                    listOf(TypeMetadata(
                        ClassNames.kotlinString, listOf())))
            }
        }
    }
}