package tech.kzen.auto.client.objects.document.sequence.step.display

import tech.kzen.auto.client.objects.document.sequence.model.SequenceStore
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.locate.ObjectLocation


external interface SequenceStepDisplayProps: react.Props {
    var common: SequenceStepDisplayPropsCommon
}


data class SequenceStepDisplayPropsCommon(
    var objectLocation: ObjectLocation,
    var attributeNesting: AttributeNesting,

//    var stepController: StepDisplayManager.Wrapper,
//    var sequenceCommander: SequenceCommander,

//    var logicTraceSnapshot: LogicTraceSnapshot?,
//    var nextToRun: ObjectLocation?,

    var managed: Boolean = false,
    var first: Boolean = false,
    var last: Boolean = false,

    val sequenceStore: SequenceStore
)