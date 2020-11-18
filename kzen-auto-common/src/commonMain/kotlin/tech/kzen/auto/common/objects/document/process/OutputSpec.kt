package tech.kzen.auto.common.objects.document.process

import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.common.util.RequestParams
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
    val savePath: String,
    val previewStart: Long,
    val previewCount: Int
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofPreviewRequest(request: DetachedRequest): OutputSpec {
            val savePath = request.getSingle(ProcessConventions.saveFileKey)!!
            val startRow = request.getLong(ProcessConventions.previewStartKey)!!
            val rowCount = request.getInt(ProcessConventions.previewRowCountKey)!!
            return OutputSpec(savePath, startRow, rowCount)
        }
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
            check(attributeName == ProcessConventions.outputAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val outputNotation = graphStructure
                .graphNotation
                .mergeAttribute(objectLocation, ProcessConventions.outputAttributeName)
                    as MapAttributeNotation

            val savePath = outputNotation
                .get(ProcessConventions.saveFileKey)
                ?.asString()!!

            val previewStart = outputNotation
                .get(ProcessConventions.previewStartKey)
                ?.asString()
                ?.replace(",", "")
                ?.toLongOrNull()
                ?: return AttributeDefinitionAttempt.failure(
                    "'${ProcessConventions.previewStartKey}' attribute notation not found:" +
                            " $objectLocation - $attributeName")

            val previewCount = outputNotation
                .get(ProcessConventions.previewRowCountKey)
                ?.asString()
                ?.replace(",", "")
                ?.toIntOrNull()
                ?: return AttributeDefinitionAttempt.failure(
                    "'${ProcessConventions.previewRowCountKey}' attribute notation not found:" +
                            " $objectLocation - $attributeName")

            val spec = OutputSpec(savePath, previewStart, previewCount)

            return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(spec))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun previewStartZeroBased(): Long {
        return previewStart - 1
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toPreviewRequest(): DetachedRequest {
        return DetachedRequest(
            RequestParams.of(
                ProcessConventions.saveFileKey to savePath,
                ProcessConventions.previewStartKey to previewStart.toString(),
                ProcessConventions.previewRowCountKey to previewCount.toString()
            ), null)
    }
}