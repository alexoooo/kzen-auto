package tech.kzen.auto.common.objects.document.target

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions


@Reflect
class TargetDocument(
    val objectLocation: ObjectLocation,
    val documentNotation: DocumentNotation
):
    DocumentArchetype()
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val targetJvmPath = DocumentPath.parse("auto-jvm/target/target-jvm.yaml")


        val screenshotTakerLocation = ObjectLocation(
            targetJvmPath,
            ObjectPath(ObjectName("ScreenshotTaker"), ObjectNesting.root))


        val archetypeObjectName = ObjectName("Target")


        fun isTarget(documentNotation: DocumentNotation): Boolean {
            val mainObjectNotation =
                    documentNotation.objects.notations[NotationConventions.mainObjectPath]
                    ?: return false

            val mainObjectIs =
                    mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
                    ?: return false

            return mainObjectIs == archetypeObjectName.value
        }
    }
}
