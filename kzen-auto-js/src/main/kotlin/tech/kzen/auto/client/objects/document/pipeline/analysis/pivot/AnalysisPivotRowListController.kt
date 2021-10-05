package tech.kzen.auto.client.objects.document.pipeline.analysis.pivot

import kotlinx.browser.document
import kotlinx.css.*
import react.RBuilder
import react.RPureComponent
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.pipeline.PipelineController
import tech.kzen.auto.client.objects.document.pipeline.analysis.model.PipelineAnalysisStore
import tech.kzen.auto.client.wrap.select.ReactSelectMulti
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec
import kotlin.js.Json
import kotlin.js.json


class AnalysisPivotRowListController(
    props: Props
):
    RPureComponent<AnalysisPivotRowListController.Props, react.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var spec: PivotSpec
        var inputAndCalculatedColumns: HeaderListing?
        var analysisStore: PipelineAnalysisStore
        var runningOrLoading: Boolean
    }


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
    override fun RBuilder.render() {
        val columnListing = props.inputAndCalculatedColumns
            ?: return

        styledDiv {
            css {
                borderTopWidth = PipelineController.separatorWidth
                borderTopColor = PipelineController.separatorColor
                borderTopStyle = BorderStyle.solid
                paddingTop = 0.5.em
            }

            styledSpan {
                css {
                    fontSize = 1.5.em
                }
                +"Rows"
            }

            child(ReactSelectMulti::class) {
                attrs {
                    isMulti = true

                    value = props.spec.rows.values.map { ReactSelectOption(it, it) }.toTypedArray()

                    options = columnListing.values.map { ReactSelectOption(it, it) }.toTypedArray()

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
}