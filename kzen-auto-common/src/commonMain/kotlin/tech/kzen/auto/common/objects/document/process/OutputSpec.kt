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
import tech.kzen.lib.common.reflect.Reflect


data class OutputSpec(
    val previewStart: Long,
    val previewCount: Int
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofPreviewRequest(request: DetachedRequest): OutputSpec {
            val startRow = request.getLong(ProcessConventions.previewStartRowKey)!!
            val rowCount = request.getInt(ProcessConventions.previewRowCountKey)!!
            return OutputSpec(startRow, rowCount)
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

            val previewStart = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, ProcessConventions.previewStartPath)
                ?.asString()
                ?.replace(",", "")
                ?.toLongOrNull()
                ?: return AttributeDefinitionAttempt.failure(
                    "'${ProcessConventions.previewStartPath}' attribute notation not found:" +
                            " $objectLocation - $attributeName")

            val previewCount = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, ProcessConventions.previewCountPath)
                ?.asString()
                ?.replace(",", "")
                ?.toIntOrNull()
                ?: return AttributeDefinitionAttempt.failure(
                    "'${ProcessConventions.previewCountPath}' attribute notation not found:" +
                            " $objectLocation - $attributeName")

            val spec = OutputSpec(previewStart, previewCount)

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
                ProcessConventions.previewStartRowKey to previewStart.toString(),
                ProcessConventions.previewRowCountKey to previewCount.toString()
            ), null)
    }
}