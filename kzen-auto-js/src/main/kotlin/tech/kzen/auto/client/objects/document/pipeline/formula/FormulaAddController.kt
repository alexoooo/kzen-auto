package tech.kzen.auto.client.objects.document.pipeline.formula

import kotlinx.css.*
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.RBuilder
import react.RPureComponent
import react.dom.attrs
import react.setState
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.pipeline.formula.model.PipelineFormulaState
import tech.kzen.auto.client.objects.document.pipeline.formula.model.PipelineFormulaStore
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.material.CancelIcon
import tech.kzen.auto.client.wrap.material.MaterialIconButton
import tech.kzen.auto.client.wrap.material.MaterialTextField
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec


class FormulaAddController(
    props: Props
):
    RPureComponent<FormulaAddController.Props, FormulaAddController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var formulaSpec: FormulaSpec
        var formulaState: PipelineFormulaState
        var columnListing: List<String>?
        var runningOrLoading: Boolean
        var formulaStore: PipelineFormulaStore
    }


    interface State: react.State {
        var adding: Boolean
        var selectedColumn: String?
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        adding = false
        selectedColumn = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAdd() {
        setState {
            adding = true
            selectedColumn = null
        }
    }


    private fun onCancel() {
        setState {
            adding = false
        }
    }


    private fun onSubmit() {
        val columnName = state.selectedColumn
            ?: return

        props.formulaStore.addFormulaAsync(columnName)

        setState {
            adding = false
            selectedColumn = null
        }
    }


    private fun onValueChange(columnName: String) {
        setState {
            selectedColumn = columnName
        }
    }


    private fun handleEnterAndEscape(event: KeyboardEvent) {
        ClientInputUtils.handleEnterAndEscape(
            event, ::onSubmit, ::onCancel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            if (state.adding) {
                css {
                    marginTop = 0.5.em
                }
            }

            if (! props.formulaState.formulaLoading &&
                    props.formulaState.formulaError != null
            ) {
                styledDiv {
                    +"Error: ${props.formulaState.formulaError}"
                }
            }

            if (state.adding) {
                styledDiv {
                    css {
                        display = Display.inlineBlock
                        width = 15.em
                    }

                    renderName()
                }

                renderCancelAndSubmit()
            }
            else {
                renderAddButton()
            }
        }
    }


    private fun RBuilder.renderAddButton() {
        styledDiv {
            attrs {
                title = "Add column filter"
            }

            css {
                display = Display.inlineBlock
            }

            child(MaterialIconButton::class) {
                attrs {
                    onClick = {
                        onAdd()
                    }
                }

                child(AddCircleOutlineIcon::class) {}
            }
        }
    }


    private fun RBuilder.renderCancelAndSubmit() {
        styledDiv {
            css {
                display = Display.inlineBlock
                marginTop = 0.5.em
            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Add new calculated column"
                    onClick = {
                        onSubmit()
                    }
                }

                child(AddCircleOutlineIcon::class) {}
            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Cancel adding calculated column"
                    onClick = {
                        onCancel()
                    }
                }

                child(CancelIcon::class) {}
            }
        }
    }


    private fun RBuilder.renderName() {
        child(MaterialTextField::class) {
            attrs {
                label = "Calculated column name"
                fullWidth = true

                onChange = {
                    val target = it.target as HTMLInputElement
                    onValueChange(target.value)
                }

                disabled = props.runningOrLoading
                error = (props.columnListing?.contains(state.selectedColumn) ?: false)

                onKeyDown = ::handleEnterAndEscape
            }
        }
    }
}