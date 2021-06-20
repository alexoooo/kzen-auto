package tech.kzen.auto.common.objects.document.report.spec.analysis

import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class AnalysisSpec(
    val type: AnalysisType,
    val flat: AnalysisFlatDataSpec,
    val pivot: PivotSpec
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val typeKey = AttributeSegment.ofKey("type")
        val typeAttributePath = ReportConventions.analysisAttributePath.nest(typeKey)

        private val flatKey = AttributeSegment.ofKey("flat")
        val flatAttributePath = ReportConventions.analysisAttributePath.nest(flatKey)

        private val pivotKey = AttributeSegment.ofKey("pivot")
        val pivotAttributePath = ReportConventions.analysisAttributePath.nest(pivotKey)


        fun changeTypeCommand(mainLocation: ObjectLocation, type: AnalysisType): NotationCommand {
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
            check(attributeName == ReportConventions.analysisAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val typeAttributeNotation = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, typeAttributePath) as? ScalarAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'$typeAttributePath' attribute notation not found:" +
                            " $objectLocation - $attributeName")
            val analysisType = AnalysisType.valueOf(typeAttributeNotation.value)

            val flatNotation = graphStructure
                .graphNotation
                .mergeAttribute(objectLocation, flatAttributePath) as? MapAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'$flatAttributePath' attribute notation not found:" +
                            " $objectLocation - $attributeName")
            val flat = AnalysisFlatDataSpec.ofNotation(flatNotation)

            val pivotNotation = graphStructure
                .graphNotation
                .mergeAttribute(objectLocation, pivotAttributePath) as? MapAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'$pivotAttributePath' attribute notation not found:" +
                            " $objectLocation - $attributeName")
            val pivot = PivotSpec.ofNotation(pivotNotation)

            val spec = AnalysisSpec(
                analysisType, flat, pivot)

            return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(spec))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun isEmpty(): Boolean {
//        return rows.values.isEmpty() &&
//                values.isEmpty()
//    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toRunSignature(): AnalysisSpec {
        return when (type) {
            AnalysisType.FlatData ->
                AnalysisSpec(type, flat, PivotSpec.empty)

            AnalysisType.PivotTable ->
                AnalysisSpec(type, flat, PivotSpec.empty)
        }
    }


    override fun digest(builder: Digest.Builder) {
        builder.addInt(type.ordinal)
        builder.addDigestible(flat)
        builder.addDigestible(pivot)
    }
}