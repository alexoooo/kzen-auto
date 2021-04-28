package tech.kzen.auto.client.objects.document.report.input.select

import kotlinx.browser.document
import kotlinx.css.em
import kotlinx.css.fontSize
import kotlinx.css.width
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.MaterialInputLabel
import tech.kzen.auto.client.wrap.ReactSelect
import tech.kzen.auto.client.wrap.ReactSelectOption
import tech.kzen.auto.client.wrap.reactStyle
import kotlin.js.Json
import kotlin.js.json


class InputSelectedType(
    props: Props
):
    RPureComponent<InputSelectedType.Props, InputSelectedType.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
        var editDisabled: Boolean
    }


    interface State: RState {
//        var showFolders: Boolean
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val selectId = "material-react-data-type"

        child(MaterialInputLabel::class) {
            attrs {
                htmlFor = selectId

                style = reactStyle {
                    fontSize = 0.8.em
                    width = 14.em
                }
            }

            +"Data Type"
        }

        val dataType = props.reportState.inputSpec().selection.dataType
        val className = dataType.get()
        val dataTypeLabel = className.substringAfterLast(".")

        val classNamesLabels = listOf(
            ReactSelectOption(className, dataTypeLabel))

//        +"[Type: $dataTypeLabel]"

        val selectOptions = classNamesLabels
            .toTypedArray()

        child(ReactSelect::class) {
            attrs {
                id = selectId

//                value = selectOptions.find { it.value == state.selectedColumn }
                value = classNamesLabels[0]

                options = selectOptions
//                options = optionsArray

                onChange = {
//                    console.log("^^^^^ selected: $it")
//                    onColumnSelected(it.value)
                }

                isDisabled = props.editDisabled

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