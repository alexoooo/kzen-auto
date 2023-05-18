package tech.kzen.auto.client.objects.document.sequence.step.display

import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.common.paradigm.common.v1.trace.model.LogicTraceSnapshot
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.locate.ObjectLocation


external interface SequenceStepDisplayProps: react.Props {
    var common: SequenceStepDisplayPropsCommon
}


data class SequenceStepDisplayPropsCommon(
    var clientState: SessionState,
    var objectLocation: ObjectLocation,
    var attributeNesting: AttributeNesting,

    var logicTraceSnapshot: LogicTraceSnapshot?,
    var nextToRun: ObjectLocation?,

    var managed: Boolean = false,
    var first: Boolean = false,
    var last: Boolean = false
) {
//    fun isRunning(): Boolean {
////            return objectLocation == imperativeModel.running
//        return false
//    }
//
//    fun isActive(): Boolean {
////            return clientState.activeHost != null &&
////                    imperativeModel.frames.any { it.path == clientState.navigationRoute.documentPath }
//        return false
//    }
}