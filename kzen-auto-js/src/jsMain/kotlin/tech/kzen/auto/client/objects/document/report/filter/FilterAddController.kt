package tech.kzen.auto.client.objects.document.report.filter

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.report.filter.model.ReportFilterState
import tech.kzen.auto.client.objects.document.report.filter.model.ReportFilterStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import web.cssom.Display
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface FilterAddControllerProps: Props {
    var filterStore: ReportFilterStore
    var filterState: ReportFilterState
    var filterSpec: FilterSpec
    var inputAndCalculatedColumns: HeaderListing?
}


external interface FilterAddControllerState: State {
    var adding: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class FilterAddController(
    props: FilterAddControllerProps
):
    RPureComponent<FilterAddControllerProps, FilterAddControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun FilterAddControllerState.init(props: FilterAddControllerProps) {
        adding = false
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
    override fun ChildrenBuilder.render() {
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

        div {
            if (state.adding) {
                css {
                    marginTop = 0.5.em
                }
            }

            if (!props.filterState.filterLoading) {
                if (props.filterState.filterError != null) {
                    div {
                        +"Error: ${props.filterState.filterError}"
                    }
                }

                if (state.adding) {
                    div {
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


    private fun ChildrenBuilder.renderAddButton() {
        div {
            title = "Add column filter"

            css {
                display = Display.inlineBlock
            }

            IconButton {
                onClick = {
                    onAdd()
                }

                icon("material-symbols:add-circle-outline") {}
            }
        }
    }


    private fun ChildrenBuilder.renderCancelButton() {
        div {
            title = "Cancel adding column filter"

            css {
                display = Display.inlineBlock
            }

            IconButton {
                onClick = {
                    onCancel()
                }

                icon("material-symbols:cancel") {}
            }
        }
    }


    private fun ChildrenBuilder.renderSelect(unusedOptions: List<HeaderLabel>, editDisabled: Boolean) {
        val selectOptions = unusedOptions
            .map {
                val option: SelectOption = unsafeJso {
                    value = it.asString()
                    label = it.render()
                }
                option
            }
            .toTypedArray()

        muiAutocompleteField(
            label = "Column name",
            options = selectOptions,
            selectedOption = null,
            onSelect = { onColumnSelected(it.value) },
            disabled = editDisabled,
            disableClearable = true,
            autoFocus = true,
            openOnFocus = true)
    }
}