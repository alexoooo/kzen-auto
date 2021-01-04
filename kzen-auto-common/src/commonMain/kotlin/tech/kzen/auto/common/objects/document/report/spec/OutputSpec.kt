package tech.kzen.auto.common.objects.document.report.spec

import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.reflect.Reflect


data class OutputSpec(
    val workPath: String,
    val savePath: String,
    val previewStart: Long,
    val previewCount: Int
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val defaultWorkPath = "report"

//        fun ofPreviewRequest(request: DetachedRequest): OutputSpec {
//            val savePath = request.getSingle(ReportConventions.saveFileKey)!!
//            val startRow = request.getLong(ReportConventions.previewStartKey)!!
//            val rowCount = request.getInt(ReportConventions.previewRowCountKey)!!
//            return OutputSpec(savePath, startRow, rowCount)
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    object Definer: AttributeDefiner {
        override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
        ): AttributeDefinitionAttempt {
            check(attributeName == ReportConventions.outputAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val outputNotation = graphStructure
                .graphNotation
                .mergeAttribute(objectLocation, ReportConventions.outputAttributeName)
                    as MapAttributeNotation

            val workPath = outputNotation
                .get(ReportConventions.workDirKey)
                ?.asString()!!

            val savePath = outputNotation
                .get(ReportConventions.saveFileKey)
                ?.asString()!!

            val previewStart = outputNotation
                .get(ReportConventions.previewStartKey)
                ?.asString()
                ?.replace(",", "")
                ?.toLongOrNull()
                ?: return AttributeDefinitionAttempt.failure(
                    "'${ReportConventions.previewStartKey}' attribute notation not found:" +
                            " $objectLocation - $attributeName")

            val previewCount = outputNotation
                .get(ReportConventions.previewRowCountKey)
                ?.asString()
                ?.replace(",", "")
                ?.toIntOrNull()
                ?: return AttributeDefinitionAttempt.failure(
                    "'${ReportConventions.previewRowCountKey}' attribute notation not found:" +
                            " $objectLocation - $attributeName")

            val spec = OutputSpec(workPath, savePath, previewStart, previewCount)

            return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(spec))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun previewStartZeroBased(): Long {
        return previewStart - 1
    }


    fun isDefaultWorkPath(): Boolean {
        return workPath == defaultWorkPath
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun toPreviewRequest(): DetachedRequest {
//        return DetachedRequest(
//            RequestParams.of(
//                ReportConventions.workDirKey to workPath,
//                ReportConventions.saveFileKey to savePath,
//                ReportConventions.previewStartKey to previewStart.toString(),
//                ReportConventions.previewRowCountKey to previewCount.toString()
//            ), null)
//    }
}