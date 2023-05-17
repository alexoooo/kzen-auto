package tech.kzen.auto.client.objects.document.report.input.select

import web.cssom.em
import emotion.react.css
import js.core.jso
import kotlinx.browser.document
import mui.material.InputLabel
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.span
import react.react
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.objects.document.report.input.select.model.InputSelectedState
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.common.objects.document.plugin.model.ReportDefinerDetail
import tech.kzen.auto.common.objects.document.report.spec.input.InputSelectionSpec
import kotlin.js.Json
import kotlin.js.json


//---------------------------------------------------------------------------------------------------------------------
external interface InputSelectedFormatControllerProps: react.Props {
    var spec: InputSelectionSpec
    var editDisabled: Boolean

    var inputSelectedState: InputSelectedState
    var inputStore: ReportInputStore
}


//---------------------------------------------------------------------------------------------------------------------
class InputSelectedFormatController(
    props: InputSelectedFormatControllerProps
):
    RPureComponent<InputSelectedFormatControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
        prevProps: InputSelectedFormatControllerProps,
        prevState: State,
        snapshot: Any
    ) {
//        if (state.loadedFormats != null &&
//                props.reportState.inputSpec().selection.dataType !=
//                    prevProps.reportState.inputSpec().selection.dataType
//        ) {
//            setState {
//                loadedFormats = null
//            }
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun loadIfRequired() {
        if (props.inputSelectedState.typeFormats != null) {
            return
        }

        props.inputStore.selected.listTypeFormatsAsync()
    }


    private fun onValueChange(coordinateValue: String) {
        val selected = props.inputSelectedState.selectedChecked
        if (selected.isEmpty()) {
            return
        }

        val selectedCoordinate = CommonPluginCoordinate.ofString(coordinateValue)
        val selectedSpecs = props.spec.locations.filter { it.location in selected }

        props.inputStore.selected.setFormatAsync(selectedCoordinate, selectedSpecs)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun typeLabel(reportDefinerDetail: ReportDefinerDetail): String {
        return reportDefinerDetail.coordinate.asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val selectId = "material-react-data-type"

        val selectionProcessorDefinitionCoordinates = props
            .inputSelectedState
            .selectedInfo
            ?.locations
            ?.filter { ! it.dataLocationInfo.isMissing() && ! it.invalidProcessor }
            ?.map { it.processorDefinitionCoordinate }
            ?.toSet()

        val loadedFormats = props.inputSelectedState.typeFormats
        val singleOption = loadedFormats != null && loadedFormats.size == 1

        val classNamesLabels = when {
            loadedFormats != null -> {
                val loadedOptions = loadedFormats.map {
                    val option: ReactSelectOption = jso {
                        value = it.coordinate.asString()
                        label = typeLabel(it)
                    }
                    option
                }

                if (loadedOptions.size <= 1) {
                    loadedOptions
                }
                else {
                    listOf(
                        run {
                            val option: ReactSelectOption = jso {
                                value = CommonPluginCoordinate.defaultName
                                label = "Default"
                            }
                            option
                        }
                    ) + loadedOptions
                }
            }

            selectionProcessorDefinitionCoordinates != null ->
                selectionProcessorDefinitionCoordinates.map {
                    val option: ReactSelectOption = jso {
                        value = it.name
                        label = it.asString()
                    }
                    option
                }

            else ->
                listOf()
        }

        val selectOptions = classNamesLabels
            .toTypedArray()

        val selectionEmpty = props.inputSelectedState.selectedChecked.isEmpty()

        span {
            title = when {
                singleOption ->
                    "Only one format is available"

                props.inputSelectedState.selectedChecked.isEmpty() ->
                    "Please select one or more files"

                else ->
                    "Specify format for selected files"
            }

            InputLabel {
                htmlFor = selectId
                css {
                    fontSize = 0.8.em
                    width = 16.em
                }
                +"Format"
            }

            ReactSelect::class.react {
                id = selectId

                value =
                    if (classNamesLabels.size == 1) {
                        classNamesLabels.single()
                    }
                    else {
                        null
                    }

                options = selectOptions

                onChange = {
                    onValueChange(it.value)
                }

                onMenuOpen = {
                    loadIfRequired()
                }

                isDisabled = props.editDisabled || (selectionEmpty && ! singleOption)

                // https://stackoverflow.com/a/51844542/1941359
                val styleTransformer: (Json, Json) -> Json = { base, _ ->
                    val transformed = json()
                    transformed.add(base)
                    transformed["background"] = "transparent"
                    transformed["borderWidth"] = "2px"
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