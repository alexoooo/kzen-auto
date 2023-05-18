package tech.kzen.auto.client.objects.document.report.analysis.pivot

import emotion.react.css
import mui.material.Size
import mui.material.ToggleButton
import mui.material.ToggleButtonGroup
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.report.analysis.model.ReportAnalysisStore
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueColumnSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueType
import web.cssom.FontWeight


//---------------------------------------------------------------------------------------------------------------------
external interface AnalysisPivotValueTypeControllerProps: react.Props {
    var columnName: String
    var pivotValueSpec: PivotValueColumnSpec
    var analysisStore: ReportAnalysisStore
}


//---------------------------------------------------------------------------------------------------------------------
class AnalysisPivotValueTypeController(
    props: AnalysisPivotValueTypeControllerProps
):
    RPureComponent<AnalysisPivotValueTypeControllerProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private fun onTypeChange(valueTypes: Array<String>) {
        val oldTypes = props.pivotValueSpec.types
        val newTypes = valueTypes.map { PivotValueType.valueOf(it) }

        val added = newTypes.filter { it !in oldTypes }
        val removed = oldTypes.filter { it !in newTypes }

        val changeCount = added.size + removed.size

        check(changeCount != 0) { "No change" }
        check(changeCount <= 1) { "Multiple changes" }

        if (added.isNotEmpty()) {
            props.analysisStore.addValueType(
                props.columnName, added.single())
        }
        else {
            props.analysisStore.removeValueType(
                props.columnName, removed.single())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        ToggleButtonGroup {
            exclusive = false

            value = props.pivotValueSpec.types.map { it.name }.toTypedArray()

            onChange = { _, v ->
                onTypeChange(v)
            }

            size = Size.small

            for (valueType in PivotValueType.values()) {
                ToggleButton {
                    key = valueType.name
                    value = valueType.name
                    size = Size.small
                    span {
                        css {
                            fontWeight = FontWeight.bold
                        }
                        +valueType.name
                    }
                }
            }
        }
    }
}