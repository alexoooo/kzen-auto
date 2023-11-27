package tech.kzen.auto.common.objects.document.sequence

import tech.kzen.auto.common.paradigm.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName


object SequenceConventions {
    val sequenceValidatorLocation = ObjectLocation.parse(
        "auto-jvm/sequence/sequence-jvm.yaml#SequenceValidator")

    val objectName = ObjectName("Sequence")
    val stepObjectName = ObjectName("SequenceStep")

    val stepsAttributeName = AttributeName("steps")
    val stepsAttributePath = AttributePath.ofName(stepsAttributeName)

    val nextStepTracePath = LogicTracePath(listOf("next-step"))
}