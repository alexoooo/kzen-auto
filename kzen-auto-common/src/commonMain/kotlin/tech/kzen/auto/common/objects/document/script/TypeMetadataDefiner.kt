package tech.kzen.auto.common.objects.document.script

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassName


// Coerces a structured `type` notation map into a TypeMetadata at definition time (once), so a binding's
// constructor receives the type directly. The notation shape mirrors TypeMetadata: a map of
// { class: <qualified class name>, generics: [ <nested type maps> ], nullable: <bool> }. Bound via the
// TypeMetadata archetype's meta.ref (mirrors ResourceClosePolicyDefiner / TargetSpecDefiner).
@Reflect
class TypeMetadataDefiner: AttributeDefiner {
    companion object {
        const val classKey = "class"
        const val genericsKey = "generics"
        const val nullableKey = "nullable"


        fun parse(attributeNotation: AttributeNotation): TypeMetadata? {
            val mapNotation = attributeNotation as? MapAttributeNotation
                ?: return null

            val className = mapNotation.get(classKey)?.asString()
                ?: return null

            val generics =
                when (val genericsNotation = mapNotation.get(genericsKey)) {
                    null ->
                        listOf()

                    is ListAttributeNotation ->
                        genericsNotation.values.map { parse(it) ?: return null }

                    else ->
                        return null
                }

            val nullable = mapNotation.get(nullableKey)?.asString()?.toBoolean() ?: false

            return TypeMetadata(ClassName(className), generics, nullable)
        }
    }


    override fun define(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        partialGraphDefinition: GraphDefinition,
        partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        val notation = graphStructure
            .graphNotation
            .firstAttribute(objectLocation, attributeName)

        val typeMetadata = parse(notation)
            ?: return AttributeDefinitionAttempt.failure(
                "Invalid TypeMetadata: $objectLocation - $attributeName - $notation")

        return AttributeDefinitionAttempt.success(
            ValueAttributeDefinition(typeMetadata))
    }
}
