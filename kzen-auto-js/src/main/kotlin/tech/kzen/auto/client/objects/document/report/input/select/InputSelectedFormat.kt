package tech.kzen.auto.client.objects.document.report.input.select

import kotlinx.browser.document
import kotlinx.css.em
import kotlinx.css.fontSize
import kotlinx.css.width
import kotlinx.html.title
import react.*
import react.dom.attrs
import react.dom.span
import tech.kzen.auto.client.objects.document.report.state.*
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.material.MaterialInputLabel
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.common.objects.document.plugin.model.ProcessorDefinerDetail
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.platform.collect.PersistentSet
import kotlin.js.Json
import kotlin.js.json


class InputSelectedFormat(
    props: Props
):
    RPureComponent<InputSelectedFormat.Props, InputSelectedFormat.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
        var editDisabled: Boolean
        var selected: PersistentSet<DataLocation>
    }


    interface State: RState {
        var loadedFormats: List<ProcessorDefinerDetail>?
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        loadedFormats = null
    }


    override fun componentDidUpdate(
        prevProps: Props,
        prevState: State,
        snapshot: Any
    ) {
        if (state.loadedFormats != null &&
                props.reportState.inputSpec().selection.dataType !=
                    prevProps.reportState.inputSpec().selection.dataType
        ) {
            setState {
                loadedFormats = null
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun loadIfRequired() {
        if (state.loadedFormats != null) {
            return
        }

        async {
            val effects = props.dispatcher.dispatch(PluginFormatsRequest)

            val formats = effects.filterIsInstance<PluginFormatsResult>().first().formats
                ?: return@async

            setState {
                loadedFormats = formats
            }
        }
    }


    private fun onValueChange(coordinateValue: String) {
        val selected = props.selected
        if (selected.isEmpty()) {
            return
        }

        val selectedCoordinate = CommonPluginCoordinate.ofString(coordinateValue)
        val selectedSpecs = props.reportState.inputSpec().selection.locations.filter { it.location in selected }

        if (selectedCoordinate.isDefault()) {
            val selectedLocations = selectedSpecs.map { it.location }

            async {
                val effects = props.dispatcher.dispatch(PluginPathInfoRequest(selectedLocations))

                val selectedLocationSpecs = effects.filterIsInstance<PluginPathInfoResult>().first().paths
                    ?: return@async

                val defaultCoordinateSet = selectedLocationSpecs.map { it.processorDefinitionCoordinate }.toSet()

                if (defaultCoordinateSet.size == 1) {
                    val defaultCoordinate = defaultCoordinateSet.single()

                    val changedLocations = selectedSpecs
                        .filter { it.processorDefinitionCoordinate != defaultCoordinate }
                        .map { it.location }

                    if (changedLocations.isEmpty()) {
                        return@async
                    }

//                    console.log("^%$^%$^%$ InputsSelectionFormatRequest - $defaultCoordinate - $changedLocations")
                    props.dispatcher.dispatch(InputsSelectionFormatRequest(
                        defaultCoordinate, changedLocations))
                }
                else {
                    val locationFormats = selectedLocationSpecs.associate {
                        it.location to it.processorDefinitionCoordinate
                    }

//                    console.log("^%$^%$^%$ InputsSelectionMultiFormatRequest - $locationFormats")
                    props.dispatcher.dispatch(InputsSelectionMultiFormatRequest(
                        locationFormats))
                }
            }
        }
        else {
            val changedLocations = selectedSpecs
                .filter { it.processorDefinitionCoordinate != selectedCoordinate }
                .map { it.location }

            if (changedLocations.isEmpty()) {
                return
            }

//            console.log("^%$^%$^%$ InputsSelectionFormatRequest - $selectedCoordinate - $changedLocations")
            props.dispatcher.dispatchAsync(InputsSelectionFormatRequest(
                selectedCoordinate, changedLocations))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun typeLabel(processorDefinerDetail: ProcessorDefinerDetail): String {
        return processorDefinerDetail.coordinate.asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val selectId = "material-react-data-type"

        val selectionProcessorDefinitionCoordinates = props
            .reportState
            .inputSelection
            ?.locations
            ?.filter { ! it.dataLocationInfo.isMissing() && ! it.invalidProcessor }
            ?.map { it.processorDefinitionCoordinate }
            ?.toSet()

        val loadedFormats = state.loadedFormats
        val singleOption = loadedFormats != null && loadedFormats.size == 1

        val classNamesLabels = when {
            loadedFormats != null -> {
                val loadedOptions = loadedFormats.map {
                    ReactSelectOption(it.coordinate.asString(), typeLabel(it))
                }

                if (loadedOptions.size <= 1) {
                    loadedOptions
                }
                else {
                    listOf(
                        ReactSelectOption(CommonPluginCoordinate.defaultName, "Default")
                    ) + loadedOptions
                }
            }

            selectionProcessorDefinitionCoordinates != null ->
                selectionProcessorDefinitionCoordinates
                    .map { ReactSelectOption(it.name, it.asString()) }

            else ->
                listOf()
        }

        val selectOptions = classNamesLabels
            .toTypedArray()

        val selectionEmpty = props.selected.isEmpty()

        span {
            attrs {
                title = when {
                    singleOption ->
                        "Only one format is available"

                    props.selected.isEmpty() ->
                        "Please select one or more files"

                    else ->
                        "Specify format for selected files"
                }
            }

            child(MaterialInputLabel::class) {
                attrs {
                    htmlFor = selectId

                    style = reactStyle {
                        fontSize = 0.8.em
                        width = 16.em
//                        color = Color.black
                    }
                }
                +"Format"
            }

            child(ReactSelect::class) {
                attrs {
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
}