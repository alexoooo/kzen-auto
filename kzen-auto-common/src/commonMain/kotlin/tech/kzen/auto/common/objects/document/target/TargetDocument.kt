package tech.kzen.auto.common.objects.document.target

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.lib.common.model.attribute.AttributeName
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


        val targetLocateLocation = ObjectLocation(
            targetJvmPath,
            ObjectPath(ObjectName("TargetLocateAction"), ObjectNesting.root))

        const val paramTarget = "target"

        /**
         * Marks DOM elements that display target patches or screenshots (previews, crop lists,
         * capture surfaces) so that a script automating the kzen-auto UI itself never treats a
         * preview of a target as the target: the locator drops visual matches inside a marked
         * element.
         */
        const val previewDataAttribute = "data-kzen-target-preview"


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


        fun hasCrops(documentNotation: DocumentNotation): Boolean {
            val resources = documentNotation.resources
                ?: return false

            return resources.digests.isNotEmpty()
        }


        /**
         * Match-score threshold for tolerant (NCC) matching, on the document's main object:
         * `tolerance: 0.8` accepts any window scoring at least 0.8 when exact matching finds
         * nothing. Absent or [exactTolerance] (or higher) means exact-only — every pre-existing
         * document keeps today's behaviour.
         */
        fun tolerance(documentNotation: DocumentNotation): Double? {
            val mainObjectNotation =
                documentNotation.objects.notations[NotationConventions.mainObjectPath]
                ?: return null

            return mainObjectNotation
                .get(toleranceAttributeName)
                ?.asString()
                ?.toDoubleOrNull()
        }


        val toleranceAttributeName = AttributeName("tolerance")

        const val exactTolerance = 1.0
    }
}
