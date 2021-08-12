package tech.kzen.auto.client.objects.document.pipeline.input.select

import kotlinx.browser.document
import kotlinx.css.em
import kotlinx.css.fontSize
import kotlinx.css.width
import kotlinx.html.title
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import react.dom.attrs
import react.dom.span
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputStore
import tech.kzen.auto.client.objects.document.pipeline.input.select.model.InputSelectedState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.material.MaterialInputLabel
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.common.objects.document.plugin.model.ProcessorDefinerDetail
import tech.kzen.auto.common.objects.document.report.spec.input.InputSelectionSpec
import kotlin.js.Json
import kotlin.js.json


class InputSelectedFormatController(
    props: Props
):
    RPureComponent<InputSelectedFormatController.Props, InputSelectedFormatController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
//        var reportState: ReportState
//        var dispatcher: ReportDispatcher
        var spec: InputSelectionSpec
        var editDisabled: Boolean
//        var selected: PersistentSet<DataLocation>

        var inputSelectedState: InputSelectedState
        var inputStore: PipelineInputStore
    }


    interface State: RState {
//        var loadedFormats: List<ProcessorDefinerDetail>?
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        loadedFormats = null
    }


    override fun componentDidUpdate(
        prevProps: Props,
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

        if (selectedCoordinate.isDefault()) {
            val selectedLocations = selectedSpecs.map { it.location }

            async {
                val result = props.inputStore.selected.defaultFormats(selectedLocations)
                    ?: return@async

                val defaultCoordinateSet = result.map { it.processorDefinitionCoordinate }.toSet()

                if (defaultCoordinateSet.size == 1) {
                    val defaultCoordinate = defaultCoordinateSet.single()

                    val changedLocations = selectedSpecs
                        .filter { it.processorDefinitionCoordinate != defaultCoordinate }
                        .map { it.location }

                    if (changedLocations.isEmpty()) {
                        return@async
                    }

//                    console.log("^%$^%$^%$ InputsSelectionFormatRequest - $defaultCoordinate - $changedLocations")
//                    props.dispatcher.dispatch(InputsSelectionFormatRequest(
//                        defaultCoordinate, changedLocations))
                }
                else {
                    val locationFormats = result.associate {
                        it.location to it.processorDefinitionCoordinate
                    }

////                    console.log("^%$^%$^%$ InputsSelectionMultiFormatRequest - $locationFormats")
//                    props.dispatcher.dispatch(InputsSelectionMultiFormatRequest(
//                        locationFormats))
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

//            props.dispatcher.dispatchAsync(InputsSelectionFormatRequest(
//                selectedCoordinate, changedLocations))
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

        val selectionEmpty = props.inputSelectedState.selectedChecked.isEmpty()

        span {
            attrs {
                title = when {
                    singleOption ->
                        "Only one format is available"

                    props.inputSelectedState.selectedChecked.isEmpty() ->
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