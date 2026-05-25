package tech.kzen.auto.client.objects.document.script.display

import react.Props
import tech.kzen.lib.common.model.location.ObjectLocation


external interface ScriptStepDisplayProps: Props {
    var common: ScriptStepDisplayPropsCommon
}


data class ScriptStepDisplayPropsCommon(
    var objectLocation: ObjectLocation,
    var indexInParent: Int,

    var first: Boolean = false,
    var last: Boolean = false
)