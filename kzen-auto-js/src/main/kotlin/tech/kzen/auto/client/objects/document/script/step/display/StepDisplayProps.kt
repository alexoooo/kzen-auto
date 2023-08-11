package tech.kzen.auto.client.objects.document.script.step.display

import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.location.ObjectLocation


external interface StepDisplayProps: react.Props {
    var common: StepDisplayPropsCommon
}


data class StepDisplayPropsCommon(
    var clientState: SessionState,
    var objectLocation: ObjectLocation,
    var attributeNesting: AttributeNesting,
    var imperativeModel: ImperativeModel,

    var managed: Boolean = false,
    var first: Boolean = false,
    var last: Boolean = false
) {
    fun isRunning(): Boolean {
        return objectLocation == imperativeModel.running
    }

    fun isActive(): Boolean {
        return clientState.activeHost != null &&
                imperativeModel.frames.any { it.path == clientState.navigationRoute.documentPath }
    }
}