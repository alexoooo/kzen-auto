package tech.kzen.auto.client.objects.document.process.pivot

import kotlinx.css.FontWeight
import kotlinx.css.fontWeight
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.css
import styled.styledSpan
import tech.kzen.auto.client.objects.document.process.state.PivotValueTypeAddRequest
import tech.kzen.auto.client.objects.document.process.state.PivotValueTypeRemoveRequest
import tech.kzen.auto.client.objects.document.process.state.ProcessDispatcher
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.client.wrap.MaterialToggleButton
import tech.kzen.auto.client.wrap.MaterialToggleButtonMultiGroup
import tech.kzen.auto.common.objects.document.process.PivotSpec
import tech.kzen.auto.common.objects.document.process.PivotValueSpec
import tech.kzen.auto.common.objects.document.process.PivotValueType


class PivotValueTypes(
    props: Props
):
    RPureComponent<PivotValueTypes.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var columnName: String,
        var pivotValueSpec: PivotValueSpec,

        var pivotSpec: PivotSpec,
        var processState: ProcessState,
        var dispatcher: ProcessDispatcher
    ): RProps


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
                            props.processState.initiating ||
                            props.processState.filterTaskRunning ||
                            props.processState.pivotLoading
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