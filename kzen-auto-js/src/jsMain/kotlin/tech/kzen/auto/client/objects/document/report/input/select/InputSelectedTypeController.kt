package tech.kzen.auto.client.objects.document.report.input.select

import emotion.react.css
import js.objects.unsafeJso
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.objects.document.report.input.select.model.InputSelectedState
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.common.objects.document.report.spec.input.InputSelectionSpec
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames.topLevelWords
import web.cssom.em


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
        val dataType = props.spec.dataType
        val selectedOption: SelectOption = unsafeJso {
            value = dataType.asString()
            label = typeLabel(dataType)
        }

        val loadedDataTypes = props.inputSelectedState.dataTypes

        val classNamesLabels =
            loadedDataTypes?.map {
                val option: SelectOption = unsafeJso {
                    value = it.asString()
                    label = typeLabel(it)
                }
                option
            }
                ?: listOf(selectedOption)

        val selectOptions = classNamesLabels
            .toTypedArray()

        div {
            css {
                width = 16.em
            }

            muiAutocompleteField(
                label = "Data Type",
                options = selectOptions,
                selectedOption = selectedOption,
                onSelect = { onValueChange(it.value) },
                onOpen = { loadIfRequired() },
                disabled = props.editDisabled,
                disableClearable = true)
        }
    }
}