package tech.kzen.auto.common.objects.document.script

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentDefinition
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.reflect.Reflect


// Parses the Script `results` notation into the output TupleDefinition (the Script's result signature).
// The notation shape is a map keyed by tuple-component name -> a TypeMetadata map (the same shape
// TypeMetadataDefiner reads), e.g. { main: { class: kotlin.String, generics: [], nullable: false } }.
// Only `main` is wired today, but the map shape leaves room for additional named results with no schema
// change. Lenient: an absent / non-map / empty `results`, or an unparseable entry, yields the empty
// TupleDefinition (a void Script) rather than failing the whole definition. Single source of truth for
// `results`, shared by ScriptDocument's constructor (via meta.by), ScriptValidator, and ScriptExecution.
@Reflect
class ResultSignatureDefiner: AttributeDefiner {
    companion object {
        fun parse(attributeNotation: AttributeNotation?): TupleDefinition {
            val mapNotation = attributeNotation as? MapAttributeNotation
                ?: return TupleDefinition.empty

            val components = mapNotation.map.mapNotNull { (segment, typeNotation) ->
                val typeMetadata = TypeMetadataDefiner.parse(typeNotation)
                    ?: return@mapNotNull null
                TupleComponentDefinition(
                    TupleComponentName(segment.asKey()), LogicType(typeMetadata))
            }

            return TupleDefinition(components)
        }
    }


    override fun define(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        partialGraphDefinition: GraphDefinition,
        partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        // AttributePath overload returns null for an absent attribute (vs. the throwing AttributeName one);
        // `results` is present via the Script archetype default, but the lenient form keeps parse() in charge.
        val notation = graphStructure
            .graphNotation
            .firstAttribute(objectLocation, AttributePath.ofName(attributeName))

        return AttributeDefinitionAttempt.success(
            ValueAttributeDefinition(parse(notation)))
    }
}
