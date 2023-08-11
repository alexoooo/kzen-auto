package tech.kzen.auto.common.objects.document.report.spec.output

import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.reflect.Reflect


data class OutputSpec(
    val type: OutputType,
    val explore: OutputExploreSpec,
    val export: OutputExportSpec,
    val workPath: String
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val defaultWorkPath = "report"

        private val typeKey = AttributeSegment.ofKey("type")
        val typeAttributePath = ReportConventions.outputAttributePath.nest(typeKey)

        private val exploreKey = AttributeSegment.ofKey("explore")
        val exploreAttributePath = ReportConventions.outputAttributePath.nest(exploreKey)

        private val exportKey = AttributeSegment.ofKey("export")
        val exportAttributePath = ReportConventions.outputAttributePath.nest(exportKey)

        private val workDirKey = AttributeSegment.ofKey("work")
        val workDirPath = ReportConventions.outputAttributePath.nest(workDirKey)

        fun changeTypeCommand(mainLocation: ObjectLocation, type: OutputType): NotationCommand {
            return UpdateInAttributeCommand(
                mainLocation,
                typeAttributePath,
                ScalarAttributeNotation(type.name))
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
            check(attributeName == ReportConventions.outputAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val typeAttributeNotation = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, typeAttributePath) as? ScalarAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'${typeAttributePath}' attribute notation not found:" +
                            " $objectLocation - $attributeName")
            val outputType = OutputType.valueOf(typeAttributeNotation.value)

            val exploreNotation = graphStructure
                .graphNotation
                .mergeAttribute(objectLocation, exploreAttributePath) as? MapAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'${exploreAttributePath}' attribute notation not found:" +
                            " $objectLocation - $attributeName")
            val explore = OutputExploreSpec.ofNotation(exploreNotation)

            val exportNotation = graphStructure
                .graphNotation
                .mergeAttribute(objectLocation, exportAttributePath) as? MapAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'${exportAttributePath}' attribute notation not found:" +
                            " $objectLocation - $attributeName")
            val export = OutputExportSpec.ofNotation(exportNotation)

            val workPathNotation = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, workDirPath) as? ScalarAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'${workDirPath}' attribute notation not found:" +
                            " $objectLocation - $attributeName")
            val workPath = workPathNotation.asString()

            val spec = OutputSpec(
                outputType, explore, export, workPath)

            return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(spec))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isDefaultWorkPath(): Boolean {
        return workPath == defaultWorkPath
    }
}