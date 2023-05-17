package tech.kzen.auto.client.objects.document.report.analysis.pivot

import web.cssom.LineStyle
import web.cssom.em
import emotion.react.css
import js.core.jso
import kotlinx.browser.document
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.react
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.analysis.model.ReportAnalysisStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.select.ReactSelectMulti
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec
import kotlin.js.Json
import kotlin.js.json


//---------------------------------------------------------------------------------------------------------------------
external interface AnalysisPivotRowListControllerProps: react.Props {
    var spec: PivotSpec
    var inputAndCalculatedColumns: HeaderListing?
    var analysisStore: ReportAnalysisStore
    var runningOrLoading: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class AnalysisPivotRowListController(
    props: AnalysisPivotRowListControllerProps
):
    RPureComponent<AnalysisPivotRowListControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private fun onOptionsChange(options: Array<ReactSelectOption>?) {
        val oldRows = props.spec.rows

        if (options.isNullOrEmpty() && oldRows.values.size > 1) {
            props.analysisStore.clearPivotRowsAsync()
        }
        else {
            val newRows = options?.map { it.value } ?: listOf()

            val added = newRows.filter { it !in oldRows.values }
            val removed = oldRows.values.filter { it !in newRows }

            val changeCount = added.size + removed.size

            check(changeCount != 0) { "No change" }
            check(changeCount <= 1) { "Multiple changes" }

            if (added.isNotEmpty()) {
                props.analysisStore.addPivotRowAsync(added.single())
            }
            else {
                props.analysisStore.removePivotRowAsync(removed.single())
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val columnListing = props.inputAndCalculatedColumns
            ?: return

        div {
            css {
                borderTopWidth = ReportController.separatorWidth
                borderTopColor = ReportController.separatorColor
                borderTopStyle = LineStyle.solid
                paddingTop = 0.5.em
            }

            span {
                css {
                    fontSize = 1.5.em
                }
                +"Rows"
            }

            ReactSelectMulti::class.react {
                isMulti = true

                value = props.spec.rows.values.map {
                    val option: ReactSelectOption = jso {
                        value = it
                        label = it
                    }
                    option
                }.toTypedArray()

                options = columnListing.values.map {
                    val option: ReactSelectOption = jso {
                        value = it
                        label = it
                    }
                    option
                }.toTypedArray()

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

                isDisabled = props.runningOrLoading
            }
        }
    }
}