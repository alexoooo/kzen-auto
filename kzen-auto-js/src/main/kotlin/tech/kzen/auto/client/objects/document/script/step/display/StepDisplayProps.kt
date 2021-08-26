package tech.kzen.auto.client.objects.document.script.step.display

import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.locate.ObjectLocation


open class StepDisplayProps(
        var common: Common
): react.Props {
    data class Common(
        var clientState: SessionState,
//        var graphStructure: GraphStructure,
        var objectLocation: ObjectLocation,
        var attributeNesting: AttributeNesting,
        var imperativeModel: ImperativeModel,

        var managed: Boolean = false,
        var first: Boolean = false,
        var last: Boolean = false/*,
        var active: Boolean = false*/
    ) {
        fun isRunning(): Boolean {
            return objectLocation == imperativeModel.running
        }

        fun isActive(): Boolean {
            return clientState.activeHost != null &&
                    imperativeModel.frames.any { it.path == clientState.navigationRoute.documentPath }
        }

//        fun graphStructure(): GraphStructure {
//            return clientState.graphDefinitionAttempt.successful.graphStructure
//        }
    }
}