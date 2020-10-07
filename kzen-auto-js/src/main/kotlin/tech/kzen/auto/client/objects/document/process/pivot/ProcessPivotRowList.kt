package tech.kzen.auto.client.objects.document.process.pivot

import kotlinx.browser.document
import kotlinx.css.em
import kotlinx.css.fontSize
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.styledDiv
import tech.kzen.auto.client.objects.document.process.state.*
import tech.kzen.auto.client.wrap.MaterialInputLabel
import tech.kzen.auto.client.wrap.ReactSelectMulti
import tech.kzen.auto.client.wrap.ReactSelectOption
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.process.PivotSpec
import kotlin.js.Json
import kotlin.js.json


class ProcessPivotRowList(
    props: Props
):
    RPureComponent<ProcessPivotRowList.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var pivotSpec: PivotSpec,
        var processState: ProcessState,
        var dispatcher: ProcessDispatcher
    ): RProps


    //-----------------------------------------------------------------------------------------------------------------
    private fun onOptionsChange(options: Array<ReactSelectOption>?) {
        val oldRows = props.pivotSpec.rows

        val action =
            if (options.isNullOrEmpty()) {
                check(oldRows.isNotEmpty()) { "Already empty" }
                PivotRowClearRequest
            }
            else {
                val newRows = options.map { it.value }

                val added = newRows.filter { it !in oldRows }
                val removed = oldRows.filter { it !in newRows }

                val changeCount = added.size + removed.size

                check(changeCount != 0) { "No change" }
                check(changeCount <= 1) { "Multiple changes" }

                if (added.isNotEmpty()) {
                    PivotRowAddRequest(added.single())
                }
                else {
                    PivotRowRemoveRequest(removed.single())
                }
            }

        props.dispatcher.dispatchAsync(action)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val columnListing = props.processState.columnListing
            ?: return

        styledDiv {
            val selectId = "material-react-select-id"

            child(MaterialInputLabel::class) {
                attrs {
                    htmlFor = selectId

                    style = reactStyle {
                        fontSize = 0.8.em
                    }
                }

                +"Rows"
            }

            child(ReactSelectMulti::class) {
                attrs {
                    isMulti = true
                    id = selectId

                    value = props.pivotSpec.rows.map { ReactSelectOption(it, it) }.toTypedArray()

                    options = columnListing.map { ReactSelectOption(it, it) }.toTypedArray()

                    onChange = {
                        onOptionsChange(it)
                    }

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

                    isDisabled = props.processState.pivotLoading
                }
            }
        }
    }
}