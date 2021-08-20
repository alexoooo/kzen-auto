package tech.kzen.auto.common.objects.document.feature

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions


// see: https://en.wikipedia.org/wiki/Feature_(computer_vision)
@Reflect
class FeatureDocument(
    val objectLocation: ObjectLocation,
    val documentNotation: DocumentNotation
):
    DocumentArchetype()
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val featureJvmPath = DocumentPath.parse("auto-jvm/feature/feature-jvm.yaml")


        val screenshotTakerLocation = ObjectLocation(
            featureJvmPath,
            ObjectPath(ObjectName("ScreenshotTaker"), ObjectNesting.root))


        val screenshotCropperLocation = ObjectLocation(
            featureJvmPath,
            ObjectPath(ObjectName("ScreenshotCropper"), ObjectNesting.root))


        val cropTopParam = "y"
        val cropLeftParam = "x"
        val cropWidthParam = "width"
        val cropHeightParam = "height"

        val archetypeObjectName = ObjectName("Feature")


        fun isFeature(documentNotation: DocumentNotation): Boolean {
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
