package tech.kzen.auto.client.objects.document.report.filter

import web.cssom.Display
import web.cssom.em
import emotion.react.css
import js.core.jso
import kotlinx.browser.document
import mui.material.IconButton
import mui.material.InputLabel
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.react
import tech.kzen.auto.client.objects.document.report.filter.model.ReportFilterState
import tech.kzen.auto.client.objects.document.report.filter.model.ReportFilterStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.AddCircleOutlineIcon
import tech.kzen.auto.client.wrap.material.CancelIcon
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import kotlin.js.Json
import kotlin.js.json


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

            if (! props.filterState.filterLoading) {
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

                AddCircleOutlineIcon::class.react {}
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

                CancelIcon::class.react {}
            }
        }
    }


    private fun ChildrenBuilder.renderSelect(unusedOptions: List<String>, editDisabled: Boolean) {
        val selectId = "material-react-select-id"

        InputLabel {
            htmlFor = selectId

            css {
                fontSize = 0.8.em
            }

            +"Column name"
        }

        val selectOptions = unusedOptions
            .map {
                val option: ReactSelectOption = jso {
                    value = it
                    label = it
                }
                option
            }
            .toTypedArray()

        ReactSelect::class.react {
            id = selectId

            value = null
            options = selectOptions

            onChange = {
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