package tech.kzen.auto.client.objects.document.report.input.select

import kotlinx.browser.document
import kotlinx.css.em
import kotlinx.css.fontSize
import kotlinx.css.width
import react.*
import tech.kzen.auto.client.objects.document.report.state.*
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.material.MaterialInputLabel
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames.topLevelWords
import kotlin.js.Json
import kotlin.js.json


class InputSelectedType(
    props: Props
):
    RPureComponent<InputSelectedType.Props, InputSelectedType.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
        var editDisabled: Boolean
    }


    interface State: react.State {
        var loadedDataTypes: List<ClassName>?
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        loadedDataTypes = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun loadIfRequired() {
        if (state.loadedDataTypes != null) {
            return
        }

        async {
            val effects = props.dispatcher.dispatch(PluginDataTypesRequest)

            val dataTypes = effects.filterIsInstance<PluginDataTypesResult>().first().dataTypes
                ?: return@async

            setState {
                loadedDataTypes = dataTypes
            }
        }
    }


    private fun onValueChange(classNameValue: String) {
        val dataType = props.reportState.inputSpec().selection.dataType
        if (dataType.asString() == classNameValue) {
            return
        }

        val className = ClassName(classNameValue)

        props.dispatcher.dispatchAsync(InputsSelectionDataTypeRequest(className))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun typeLabel(className: ClassName): String {
        val words = className.topLevelWords()

        val adjustedWords =
            if (words.size > 1 && words.last() == "Record") {
                words.subList(0, words.size - 1)
            }
            else {
                words
            }

        return adjustedWords.joinToString(" ")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val selectId = "material-react-data-type"

        child(MaterialInputLabel::class) {
            attrs {
                htmlFor = selectId

                style = reactStyle {
                    fontSize = 0.8.em
                    width = 16.em
//                    color = Color.black
                }
            }

            +"Data Type"
        }

        val dataType = props.reportState.inputSpec().selection.dataType
        val selectedOption = ReactSelectOption(dataType.asString(), typeLabel(dataType))

        val loadedDataTypes = state.loadedDataTypes

        val classNamesLabels =
            loadedDataTypes?.map {
                ReactSelectOption(it.asString(), typeLabel(it))
            }
            ?: listOf(selectedOption)

        val selectOptions = classNamesLabels
            .toTypedArray()

        child(ReactSelect::class) {
            attrs {
                id = selectId

//                value = selectOptions.find { it.value == state.selectedColumn }
                value = selectedOption

                options = selectOptions
//                options = optionsArray

                onChange = {
                    onValueChange(it.value)
                }

                onMenuOpen = {
                    loadIfRequired()
                }

                isDisabled = props.editDisabled

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