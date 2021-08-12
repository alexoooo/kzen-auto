package tech.kzen.auto.client.objects.document.pipeline.input.select

import kotlinx.browser.document
import kotlinx.css.em
import kotlinx.css.fontSize
import kotlinx.css.width
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputStore
import tech.kzen.auto.client.objects.document.pipeline.input.select.model.InputSelectedState
import tech.kzen.auto.client.wrap.material.MaterialInputLabel
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.common.objects.document.report.spec.input.InputSelectionSpec
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames.topLevelWords
import kotlin.js.Json
import kotlin.js.json


class InputSelectedTypeController(
    props: Props
):
    RPureComponent<InputSelectedTypeController.Props, InputSelectedTypeController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var spec: InputSelectionSpec

//        var reportState: ReportState
//        var dispatcher: ReportDispatcher

        var editDisabled: Boolean

        var inputSelectedState: InputSelectedState
        var inputStore: PipelineInputStore
    }


    interface State: RState {
//        var loadedDataTypes: List<ClassName>?
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
//        loadedDataTypes = props.inputSelectedState.dataTypes
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun loadIfRequired() {
        if (props.inputSelectedState.dataTypes != null) {
            return
        }

        props.inputStore.selected.listDataTypesAsync()
    }


    private fun onValueChange(classNameValue: String) {
        val dataType = props.spec.dataType
        if (dataType.asString() == classNameValue) {
            return
        }

        val className = ClassName(classNameValue)

        props.inputStore.selected.selectDataTypeAsync(className)
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
                }
            }

            +"Data Type"
        }

        val dataType = props.spec.dataType
        val selectedOption = ReactSelectOption(dataType.asString(), typeLabel(dataType))

        val loadedDataTypes = props.inputSelectedState.dataTypes

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

                value = selectedOption

                options = selectOptions

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