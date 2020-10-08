package tech.kzen.auto.client.objects.document.process.pivot

import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import tech.kzen.auto.client.objects.document.process.state.ProcessDispatcher
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.common.objects.document.process.PivotSpec
import tech.kzen.auto.common.objects.document.process.PivotValueSpec


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
//    private fun onDelete() {
//        props.dispatcher.dispatchAsync(
//            PivotValueRemoveRequest(props.columnName)
//        )
//    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        +" - ${props.pivotValueSpec.types}"
    }
}