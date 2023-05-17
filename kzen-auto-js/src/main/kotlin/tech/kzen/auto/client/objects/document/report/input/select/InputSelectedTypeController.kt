package tech.kzen.auto.client.objects.document.report.input.select

import web.cssom.em
import emotion.react.css
import js.core.jso
import kotlinx.browser.document
import mui.material.InputLabel
import react.ChildrenBuilder
import react.State
import react.react
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.objects.document.report.input.select.model.InputSelectedState
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.select.ReactSelect
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.common.objects.document.report.spec.input.InputSelectionSpec
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames.topLevelWords
import kotlin.js.Json
import kotlin.js.json


//---------------------------------------------------------------------------------------------------------------------
external interface InputSelectedTypeControllerProps: react.Props {
    var spec: InputSelectionSpec
    var editDisabled: Boolean
    var inputSelectedState: InputSelectedState
    var inputStore: ReportInputStore
}


//---------------------------------------------------------------------------------------------------------------------
class InputSelectedTypeController(
    props: InputSelectedTypeControllerProps
):
    RPureComponent<InputSelectedTypeControllerProps, State>(props)
{
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
    override fun ChildrenBuilder.render() {
        val selectId = "material-react-data-type"

        InputLabel {
            htmlFor = selectId

            css {
                fontSize = 0.8.em
                width = 16.em
            }

            +"Data Type"
        }

        val dataType = props.spec.dataType
        val selectedOption: ReactSelectOption = jso {
            value = dataType.asString()
            label = typeLabel(dataType)
        }

        val loadedDataTypes = props.inputSelectedState.dataTypes

        val classNamesLabels =
            loadedDataTypes?.map {
                val option: ReactSelectOption = jso {
                    value = it.asString()
                    label = typeLabel(it)
                }
                option
            }
            ?: listOf(selectedOption)

        val selectOptions = classNamesLabels
            .toTypedArray()

        ReactSelect::class.react {
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