package tech.kzen.auto.client.objects.document.report.filter

import kotlinx.browser.document
import kotlinx.css.*
import kotlinx.html.title
import react.RBuilder
import react.RPureComponent
import react.dom.attrs
import react.setState
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.report.filter.model.ReportFilterState
import tech.kzen.auto.client.objects.document.report.filter.model.ReportFilterStore
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.material.CancelIcon
import tech.kzen.auto.client.wrap.material.MaterialIconButton
import tech.kzen.auto.client.wrap.material.MaterialInputLabel
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import kotlin.js.Json
import kotlin.js.json


class FilterAddController(
    props: Props
):
    RPureComponent<FilterAddController.Props, FilterAddController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var filterStore: ReportFilterStore
        var filterState: ReportFilterState
        var filterSpec: FilterSpec
        var inputAndCalculatedColumns: HeaderListing?
    }


    interface State: react.State {
        var adding: Boolean
//        var selectedColumn: String?
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        adding = false
//        selectedColumn = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAdd() {
        setState {
            adding = true
//            selectedColumn = null
        }
    }


    private fun onCancel() {
        setState {
            adding = false
        }
    }


    private fun onColumnSelected(columnName: String) {
//        setState {
//            selectedColumn = columnName
//            adding = false
//        }

        props.filterStore.addFilterAsync(columnName)

        setState {
            adding = false
//            selectedColumn = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val availableColumns = props.inputAndCalculatedColumns
            ?: return

        val filterSpec = props.filterSpec
        val unusedOptions = availableColumns
            .values
            .filter { it !in filterSpec.columns }

        if (unusedOptions.isEmpty()) {
            return
        }

        val editDisabled =
            false
//            props.reportState.isInitiating()

        styledDiv {
            if (state.adding) {
                css {
                    marginTop = 0.5.em
                }
            }

            if (! props.filterState.filterLoading) {
                if (props.filterState.filterError != null) {
                    styledDiv {
                        +"Error: ${props.filterState.filterError}"
                    }
                }

                if (state.adding) {
                    styledDiv {
                        css {
                            display = Display.inlineBlock
                            width = 15.em
                        }

                        renderSelect(unusedOptions, editDisabled)
                    }

                    renderCancelButton()
                }
                else {
                    renderAddButton()
                }
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


    private fun RBuilder.renderCancelButton() {
        styledDiv {
            attrs {
                title = "Cancel adding column filter"
            }

            css {
                display = Display.inlineBlock
            }

            child(MaterialIconButton::class) {
                attrs {
                    onClick = {
                        onCancel()
                    }
                }

                child(CancelIcon::class) {}
            }
        }
    }


    private fun RBuilder.renderSelect(unusedOptions: List<String>, editDisabled: Boolean) {
        val selectId = "material-react-select-id"

        child(MaterialInputLabel::class) {
            attrs {
                htmlFor = selectId

                style = reactStyle {
                    fontSize = 0.8.em
                }
            }

            +"Column name"
        }

        val selectOptions = unusedOptions
            .map { ReactSelectOption(it, it) }
            .toTypedArray()

        child(ReactSelect::class) {
            attrs {
                id = selectId

                value = null
//                value = selectOptions.find { it.value == state.selectedColumn }
//                value = firstOption

                options = selectOptions
//                options = optionsArray

                onChange = {
//                    console.log("^^^^^ selected: $it")
                    onColumnSelected(it.value)
                }

                isDisabled = editDisabled

                // https://stackoverflow.com/a/51844542/1941359
                val styleTransformer: (Json, Json) -> Json = { base, _ ->
                    val transformed = json()
                    transformed.add(base)
                    transformed["background"] = "transparent"
                    transformed
                }

                val reactStyles = json()
                reactStyles["control"] = styleTransformer
                styles = reactStyles

                // NB: this was causing clipping when used in ConditionalStepDisplay table,
                //   see: https://react-select.com/advanced#portaling
                menuPortalTarget = document.body!!
            }
        }
    }
}