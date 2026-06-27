package tech.kzen.auto.client.objects.document.report.analysis.pivot

import emotion.react.css
import js.objects.unsafeJso
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.report.ReportController
import tech.kzen.auto.client.objects.document.report.analysis.model.ReportAnalysisStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteMultiField
import tech.kzen.auto.common.objects.document.report.listing.HeaderLabel
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec
import web.cssom.LineStyle
import web.cssom.em


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
    private fun onOptionsChange(options: Array<SelectOption>) {
        val oldRows = props.spec.rows

        if (options.isEmpty() && oldRows.values.size > 1) {
            props.analysisStore.clearPivotRowsAsync()
        }
        else {
            val newRows = options.map { HeaderLabel.ofString(it.value) }

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

            val selectedOptions = props.spec.rows.values.map {
                val option: SelectOption = unsafeJso {
                    value = it.asString()
                    label = it.render()
                }
                option
            }.toTypedArray()

            val columnOptions = columnListing.values.map {
                val option: SelectOption = unsafeJso {
                    value = it.asString()
                    label = it.render()
                }
                option
            }.toTypedArray()

            muiAutocompleteMultiField(
                label = "Rows",
                options = columnOptions,
                selectedOptions = selectedOptions,
                onChange = { onOptionsChange(it) },
                disabled = props.runningOrLoading)
        }
    }
}