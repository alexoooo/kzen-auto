package tech.kzen.auto.common.objects.document.script

import tech.kzen.auto.common.paradigm.logic.trace.model.LogicTracePath
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.notation.NotationConventions


object ScriptConventions {
    val scriptValidatorLocation = ObjectLocation.parse(
        "auto-jvm/script/script-jvm.yaml#ScriptValidator")

    val objectName = ObjectName("Script")
    val stepObjectName = ObjectName("ScriptStep")

    val stepsAttributeName = AttributeName("steps")
    val stepsAttributePath = AttributePath.ofName(stepsAttributeName)

    val instructionsAttributeName = AttributeName("instructions")
    val instructionsAttributePath = AttributePath.ofName(instructionsAttributeName)

    val summaryAttributeName = AttributeName("summary")
    val summaryAttributePath = AttributePath.ofName(summaryAttributeName)

    val nextStepTracePath = LogicTracePath(listOf("next-step"))


    fun isManaged(attributeName: AttributeName): Boolean {
        return AutoConventions.isManaged(attributeName) ||
            attributeName == summaryAttributeName
    }


    fun isScript(documentNotation: DocumentNotation): Boolean {
        val mainObjectNotation =
            documentNotation.objects.notations[NotationConventions.mainObjectPath]
                ?: return false

        val mainObjectIs =
            mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
                ?: return false

        return mainObjectIs == objectName.value
    }
}