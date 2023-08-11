package tech.kzen.auto.client.objects.document.sequence.step.display

import tech.kzen.lib.common.model.location.ObjectLocation


external interface SequenceStepDisplayProps: react.Props {
    var common: SequenceStepDisplayPropsCommon
}


data class SequenceStepDisplayPropsCommon(
    var objectLocation: ObjectLocation,
//    var attributePath: AttributePath,
    var indexInParent: Int,

    var first: Boolean = false,
    var last: Boolean = false
)