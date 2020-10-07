package tech.kzen.auto.client.objects.document.process.pivot

import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import styled.styledDiv
import tech.kzen.auto.client.objects.document.process.state.ProcessDispatcher
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.common.objects.document.process.PivotSpec


class ProcessPivotValueList(
    props: Props
):
    RPureComponent<ProcessPivotValueList.Props, RState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var pivotSpec: PivotSpec,
        var processState: ProcessState,
        var dispatcher: ProcessDispatcher
    ): RProps


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            +"[Values] - ${props.pivotSpec.values}"
        }
    }
}