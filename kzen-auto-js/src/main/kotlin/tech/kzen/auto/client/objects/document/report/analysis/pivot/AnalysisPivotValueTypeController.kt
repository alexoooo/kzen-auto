package tech.kzen.auto.client.objects.document.report.analysis.pivot

import kotlinx.css.FontWeight
import kotlinx.css.fontWeight
import react.RBuilder
import react.RPureComponent
import react.State
import styled.css
import styled.styledSpan
import tech.kzen.auto.client.objects.document.report.analysis.model.ReportAnalysisStore
import tech.kzen.auto.client.wrap.material.MaterialToggleButton
import tech.kzen.auto.client.wrap.material.MaterialToggleButtonMultiGroup
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueColumnSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueType


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
    override fun RBuilder.render() {
        child(MaterialToggleButtonMultiGroup::class) {
            attrs {
                exclusive = false

                value = props.pivotValueSpec.types.map { it.name }.toTypedArray()

                onChange = { _, v ->
                    onTypeChange(v)
                }

                size = "small"
            }

            for (valueType in PivotValueType.values()) {
                child(MaterialToggleButton::class) {
                    attrs {
                        key = valueType.name
                        value = valueType.name
                        size = "small"
                    }
                    styledSpan {
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