package tech.kzen.auto.common.objects.service

import tech.kzen.auto.common.api.ResultCodec
import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.api.ObjectDefiner
import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.*
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.metadata.model.ObjectMetadata
import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.notation.model.GraphNotation


@Suppress("unused")
class ActionManager(
        private val handles: List<Handle>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val actionParent = "Action"
        private val className = "tech.kzen.auto.common.objects.service.ActionManager"

        private val creatorReference = ObjectReference.ofName(ObjectName("ActionManagerCreator"))

        private val handlesAttribute = AttributeName("handles")

        private const val locationKey = "location"

        private const val valueCodecKey = "valueCodec"
        private val valueCodecPath = AttributePath.ofAttribute(AttributeName(valueCodecKey))

        private const val detailCodecKey = "detailCodec"
        private val detailCodecPath = AttributePath.ofAttribute(AttributeName(detailCodecKey))
    }


    //-----------------------------------------------------------------------------------------------------------------
    data class Handle(
            val location: ObjectLocation,
            val valueCodec: ResultCodec,
            val detailCodec: ResultCodec
    )


    class Definer: ObjectDefiner {
        override fun define(
                objectLocation: ObjectLocation,
                graphNotation: GraphNotation,
                graphMetadata: GraphMetadata,
                graphDefinition: GraphDefinition,
                graphInstance: GraphInstance
        ): ObjectDefinitionAttempt {
            val handleDefinitions = mutableListOf<MapAttributeDefinition>()

            for (e in graphNotation.coalesce.values) {
                val isParameter = e.value.attributes[NotationConventions.isAttribute.attribute]
                        ?.asString()
                        ?: continue

                if (isParameter != actionParent) {
                    continue
                }

                val valueCodecReference = graphNotation.getString(objectLocation, valueCodecPath)
                val detailCodecReference = graphNotation.getString(objectLocation, detailCodecPath)

                handleDefinitions.add(MapAttributeDefinition(mapOf(
                        locationKey to ValueAttributeDefinition(e.key),
                        valueCodecKey to ReferenceAttributeDefinition(ObjectReference.parse(valueCodecReference)),
                        detailCodecKey to ReferenceAttributeDefinition(ObjectReference.parse(detailCodecReference))
                )))
            }

            val handlesDefinition = ListAttributeDefinition(handleDefinitions)

            return ObjectDefinitionAttempt(
                    ObjectDefinition(
                            className,
                            mapOf(handlesAttribute to handlesDefinition),
                            creatorReference,
                            setOf()
                    ),
                    setOf(),
                    null
            )
        }
    }


    class Creator: ObjectCreator {
        @Suppress("UNCHECKED_CAST")
        override fun create(
                objectLocation: ObjectLocation,
                objectDefinition: ObjectDefinition,
                objectMetadata: ObjectMetadata,
                objectGraph: GraphInstance
        ): Any {
            val handlesDefinition = objectDefinition.attributeDefinitions[handlesAttribute] as ListAttributeDefinition

            val handles = mutableListOf<Handle>()

            for (handleDefinition in handlesDefinition.values as List<MapAttributeDefinition>) {
                val actionLocation = (handleDefinition.values[locationKey] as ValueAttributeDefinition)
                        .value as ObjectLocation

                val valueCodecReference =
                        (handleDefinition.values[valueCodecKey] as ReferenceAttributeDefinition).objectReference!!
                val valueCodecLocation = objectGraph.objects.locate(objectLocation, valueCodecReference)
                val valueCodec = objectGraph.objects.get(valueCodecLocation) as ResultCodec

                val detailCodecReference =
                        (handleDefinition.values[detailCodecKey] as ReferenceAttributeDefinition).objectReference!!
                val detailCodecLocation = objectGraph.objects.locate(objectLocation, detailCodecReference)
                val detailCodec = objectGraph.objects.get(detailCodecLocation) as ResultCodec

                handles.add(Handle(
                        actionLocation,
                        valueCodec,
                        detailCodec))
            }

            return ActionManager(handles)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun actionLocations(): List<ObjectLocation> {
        return handles.map { it.location }
    }


    fun get(actionLocation: ObjectLocation): Handle {
        return handles.first { it.location == actionLocation }
    }
}