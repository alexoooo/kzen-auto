package tech.kzen.auto.client.objects.document.report.pivot

import kotlinx.css.FontWeight
import kotlinx.css.fontWeight
import react.RBuilder
import react.RPureComponent
import styled.css
import styled.styledSpan
import tech.kzen.auto.client.objects.document.report.state.PivotValueTypeAddRequest
import tech.kzen.auto.client.objects.document.report.state.PivotValueTypeRemoveRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.wrap.material.MaterialToggleButton
import tech.kzen.auto.client.wrap.material.MaterialToggleButtonMultiGroup
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueColumnSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueType


class PivotValueTypes(
    props: Props
):
    RPureComponent<PivotValueTypes.Props, react.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var columnName: String,
        var pivotValueSpec: PivotValueColumnSpec,

        var pivotSpec: PivotSpec,
        var reportState: ReportState,
        var dispatcher: ReportDispatcher
    ): react.Props


    //-----------------------------------------------------------------------------------------------------------------
    private fun onTypeChange(valueTypes: Array<String>) {
        val oldTypes = props.pivotValueSpec.types
        val newTypes = valueTypes.map { PivotValueType.valueOf(it) }

        val added = newTypes.filter { it !in oldTypes }
        val removed = oldTypes.filter { it !in newTypes }

        val changeCount = added.size + removed.size

        check(changeCount != 0) { "No change" }
        check(changeCount <= 1) { "Multiple changes" }

        val action =
            if (added.isNotEmpty()) {
                PivotValueTypeAddRequest(props.columnName, added.single())
            }
            else {
                PivotValueTypeRemoveRequest(props.columnName, removed.single())
            }

        props.dispatcher.dispatchAsync(action)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        +" - ${props.pivotValueSpec.types}"

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

                        disabled =
                            props.reportState.isInitiating() ||
//                            props.reportState.filterTaskRunning ||
                            props.reportState.pivotLoading
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