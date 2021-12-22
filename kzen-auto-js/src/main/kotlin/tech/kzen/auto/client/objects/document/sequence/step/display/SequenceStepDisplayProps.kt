package tech.kzen.auto.client.objects.document.sequence.step.display

import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.locate.ObjectLocation


open class SequenceStepDisplayProps(
        var common: Common
): react.Props {
    data class Common(
        var clientState: SessionState,
//        var graphStructure: GraphStructure,
        var objectLocation: ObjectLocation,
        var attributeNesting: AttributeNesting,
//        var imperativeModel: ImperativeModel,

        var managed: Boolean = false,
        var first: Boolean = false,
        var last: Boolean = false/*,
        var active: Boolean = false*/
    ) {
        fun isRunning(): Boolean {
//            return objectLocation == imperativeModel.running
            return false
        }

        fun isActive(): Boolean {
//            return clientState.activeHost != null &&
//                    imperativeModel.frames.any { it.path == clientState.navigationRoute.documentPath }
            return false
        }
    }
}