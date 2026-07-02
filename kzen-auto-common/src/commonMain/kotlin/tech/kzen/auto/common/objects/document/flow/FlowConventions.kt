package tech.kzen.auto.common.objects.document.flow

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions


/**
 * Conventions for the Flow document (the modernized "graph" / "time series", run on the Logic model).
 * Reuses the dataflow `vertices` / `edges` attribute shape so the existing
 * [tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix] reads it unchanged.
 */
object FlowConventions {
    val objectName = ObjectName("Flow")
    val edgePipeName = ObjectName("EdgePipe")

    val verticesAttributeName = AttributeName("vertices")
    val verticesAttributePath = AttributePath.ofName(verticesAttributeName)

    val edgesAttributeName = AttributeName("edges")
    val edgesAttributePath = AttributePath.ofName(edgesAttributeName)

    // A Flow's input parameters are FlowInput vertices in the `vertices` list, each carrying its name
    // in a scalar `parameter` attribute (see notation/auto-jvm/flow/flow-vertex.yaml).
    val inputVertexName = ObjectName("FlowInput")
    val parameterAttributeName = AttributeName("parameter")


    fun isFlow(documentNotation: DocumentNotation): Boolean {
        val mainObjectNotation =
            documentNotation.objects.notations[NotationConventions.mainObjectPath]
                ?: return false

        val mainObjectIs =
            mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
                ?: return false

        return mainObjectIs == objectName.value
    }


    // True when the given archetype is an EdgePipe (or a subtype) rather than a vertex — tested by inheritance
    // chain, not a direct `is`-name match, so a 3rd-party pipe subtype is recognized (see CC-17). Used by the
    // editor to route a ribbon-inserted object into `edges` vs `vertices`. Mirrors [inputParameterNames]' chain use.
    fun isPipeArchetype(graphNotation: GraphNotation, archetypeLocation: ObjectLocation): Boolean {
        return graphNotation.inheritanceChain(archetypeLocation).any {
            it.objectPath.name == edgePipeName
        }
    }


    // The Flow's input parameter names, in notation order — the client-side analogue of
    // FlowDocument.define()'s input derivation (vertices.filterIsInstance<FlowInputVertex>()...), for
    // callers that read notation directly and can't invoke the server-side define().
    fun inputParameterNames(
        graphNotation: GraphNotation,
        flowMainLocation: ObjectLocation
    ): List<String> {
        val documentNotation = graphNotation.documents[flowMainLocation.documentPath]
            ?: return listOf()

        return documentNotation
            .directNestedObjectPaths(flowMainLocation.objectPath, verticesAttributeName)
            .map { flowMainLocation.documentPath.toObjectLocation(it) }
            .filter { vertexLocation ->
                graphNotation.inheritanceChain(vertexLocation).any { it.objectPath.name == inputVertexName }
            }
            .mapNotNull {
                (graphNotation.firstAttribute(it, parameterAttributeName) as? ScalarAttributeNotation)?.value
            }
            .filter { it.isNotEmpty() }
    }
}
