package tech.kzen.auto.client.objects.document.pipeline.input.select

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputState
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputStore
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.common.objects.document.report.spec.input.InputSelectionSpec
import tech.kzen.lib.common.model.locate.ObjectLocation


class InputSelectedController(
    props: Props
):
    RPureComponent<InputSelectedController.Props, InputSelectedController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var mainLocation: ObjectLocation
        var spec: InputSelectionSpec
        var browserOpen: Boolean
        var inputState: PipelineInputState
        var inputStore: PipelineInputStore
    }


    interface State: RState {
//        var selected: PersistentSet<DataLocation>
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        if (props.spec.locations.isEmpty()) {
            return
        }

        if (props.browserOpen) {
            styledDiv {
                css {
                    borderTopWidth = ReportController.separatorWidth
                    borderTopColor = ReportController.separatorColor
                    borderTopStyle = BorderStyle.solid
                    marginTop = 1.em
                    width = 100.pct
                    fontSize = 1.5.em
                }

                +"Selected"
            }
        }

        renderErrors()

        renderActions()

        child(InputSelectedTableController::class) {
            attrs {
                showDetails = false
                spec = props.spec
                inputState = props.inputState
                inputStore = props.inputStore
            }
        }
    }


    private fun RBuilder.renderErrors() {
        val error = props.inputState.selected.selectionError()
            ?: return

        styledDiv {
            css {
                color = Color.red
            }

            +"Error: $error"
        }
    }


    private fun RBuilder.renderActions() {
        styledDiv {
            styledSpan {
                child(InputSelectedRemoveController::class) {
                    attrs {
                        mainLocation = props.mainLocation

                        disabled = false // TODO

                        inputState = props.inputState
                        inputStore = props.inputStore
                    }
                }
            }
        }
    }
}