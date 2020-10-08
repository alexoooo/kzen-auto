package tech.kzen.auto.client.objects.document.process.pivot

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.process.state.PivotValueRemoveRequest
import tech.kzen.auto.client.objects.document.process.state.ProcessDispatcher
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.client.wrap.DeleteIcon
import tech.kzen.auto.client.wrap.MaterialIconButton
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.process.PivotSpec
import tech.kzen.auto.common.objects.document.process.PivotValueSpec


class PivotValueItem(
    props: Props
):
    RPureComponent<PivotValueItem.Props, PivotValueItem.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var columnName: String,
        var pivotValueSpec: PivotValueSpec,

        var pivotSpec: PivotSpec,
        var processState: ProcessState,
        var dispatcher: ProcessDispatcher
    ): RProps

    class State(
        var hover: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        hover = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onMouseOver() {
        setState {
            hover = true
        }
    }


    private fun onMouseOut() {
        setState {
            hover = false
        }
    }


    private fun onDelete() {
        props.dispatcher.dispatchAsync(
            PivotValueRemoveRequest(props.columnName))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            attrs {
                onMouseOverFunction = {
                    onMouseOver()
                }

                onMouseOutFunction = {
                    onMouseOut()
                }
            }

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

                        if (! state.hover) {
                            visibility = Visibility.hidden
                        }
                    }

                    onClick = {
                        onDelete()
                    }

                    disabled = props.processState.pivotLoading
                }

                child(DeleteIcon::class) {}
            }
        }
    }
}