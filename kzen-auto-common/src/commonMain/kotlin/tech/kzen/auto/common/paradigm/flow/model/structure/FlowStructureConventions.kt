package tech.kzen.auto.common.paradigm.flow.model.structure

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions


/**
 * The notation vocabulary the flow paradigm itself imposes: a `vertices` list, an `edges` list, and the channel
 * markers that say which of a vertex's attributes are inputs.
 *
 * It lives with [FlowMatrix] — the reader of that shape — rather than with the Flow document that adopts it, so
 * the dependency runs one way. A document type opts IN by aliasing these (`FlowConventions` does, the way
 * `ScriptConventions` aliases `LogicConventions`); the paradigm never reaches up into a particular document to
 * ask what its attributes are called.
 */
object FlowStructureConventions {
    //-----------------------------------------------------------------------------------------------------------------
    val verticesAttributeName = AttributeName("vertices")
    val verticesAttributePath = AttributePath.ofName(verticesAttributeName)

    val edgesAttributeName = AttributeName("edges")
    val edgesAttributePath = AttributePath.ofName(edgesAttributeName)


    //-----------------------------------------------------------------------------------------------------------------
    // Notation names of the paradigm's two input channel markers (api/input/OptionalInput and RequiredInput). A
    // vertex attribute is an input when its metadata's `is` segment names one of them — matched by name rather
    // than by class, because metadata carries the declared marker, not a loaded type.
    val optionalInputName = ObjectName("OptionalInput")
    val requiredInputName = ObjectName("RequiredInput")


    fun isInput(attributeMetadataMap: MapAttributeNotation): Boolean {
        val isSegment = attributeMetadataMap[NotationConventions.isAttributeSegment]
            as? ScalarAttributeNotation
            ?: return false

        return isSegment.value == optionalInputName.value ||
            isSegment.value == requiredInputName.value
    }


    fun isRequiredInput(attributeMetadataMap: MapAttributeNotation): Boolean {
        val isSegment = attributeMetadataMap[NotationConventions.isAttributeSegment]
            as? ScalarAttributeNotation
            ?: return false

        return isSegment.value == requiredInputName.value
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun findInputs(
        vertexLocation: ObjectLocation,
        graphStructure: GraphStructure
    ): List<AttributeName> {
        return findInputs(vertexLocation, graphStructure) {
            isInput(it)
        }
    }


    fun findRequiredInputs(
        vertexLocation: ObjectLocation,
        graphStructure: GraphStructure
    ): List<AttributeName> {
        return findInputs(vertexLocation, graphStructure) {
            isRequiredInput(it)
        }
    }


    private fun findInputs(
        vertexLocation: ObjectLocation,
        graphStructure: GraphStructure,
        predicate: (MapAttributeNotation) -> Boolean
    ): List<AttributeName> {
        val cellMetadata = graphStructure.graphMetadata.objectMetadata[vertexLocation]!!

        return cellMetadata
            .attributes
            .map
            .filter {
                predicate(it.value.attributeMetadataNotation)
            }
            .map {
                it.key
            }
    }
}
