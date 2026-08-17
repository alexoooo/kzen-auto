package tech.kzen.auto.common.objects.document.flow

import tech.kzen.auto.common.paradigm.flow.model.structure.FlowStructureConventions
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation


/**
 * Conventions for the Flow document (the modernized "graph" / "time series", run on the Logic model).
 * Reuses the dataflow `vertices` / `edges` attribute shape so the existing
 * [tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix] reads it unchanged.
 */
object FlowConventions {
    val objectName = ObjectName("Flow")
    val edgePipeName = ObjectName("EdgePipe")

    // Aliases of the paradigm's own structural vocabulary, so document-side code reads naturally without the
    // paradigm having to depend on this document (same shape as ScriptConventions over LogicConventions).
    val verticesAttributeName = FlowStructureConventions.verticesAttributeName
    val verticesAttributePath = FlowStructureConventions.verticesAttributePath

    val edgesAttributeName = FlowStructureConventions.edgesAttributeName
    val edgesAttributePath = FlowStructureConventions.edgesAttributePath

    // A Flow's Logic signature lives in its vertices: each FlowInput vertex carries an input parameter
    // name in a scalar `parameter` attribute, each FlowOutput vertex a result name in a scalar `result`
    // attribute (see notation/auto-jvm/flow/flow-vertex.yaml).
    val inputVertexName = ObjectName("FlowInput")
    val parameterAttributeName = AttributeName("parameter")
    val outputVertexName = ObjectName("FlowOutput")
    val resultAttributeName = AttributeName("result")


    fun isFlow(documentNotation: DocumentNotation): Boolean {
        return AutoConventions.isMainArchetype(documentNotation, objectName)
    }


    // True when the given archetype is an EdgePipe (or a subtype) rather than a vertex — tested by inheritance
    // chain, not a direct `is`-name match, so a 3rd-party pipe subtype is recognized (see CC-17). Used by the
    // editor to route a ribbon-inserted object into `edges` vs `vertices`. Mirrors [inputParameterNames]' chain use.
    fun isPipeArchetype(graphNotation: GraphNotation, archetypeLocation: ObjectLocation): Boolean {
        return graphNotation.inheritanceChain(archetypeLocation).any {
            it.objectPath.name == edgePipeName
        }
    }


    // The Flow's input parameter names, in notation order, derived from notation only — the single
    // source of the signature's input half, shared by FlowLogicCompiler (server) and the client
    // editors. Unnamed vertices are filtered by default (a structure lint finding); the unfiltered
    // form feeds the lint itself.
    fun inputParameterNames(
        graphNotation: GraphNotation,
        flowMainLocation: ObjectLocation,
        filterEmpty: Boolean = true
    ): List<String> {
        return signatureComponentNames(
            graphNotation, flowMainLocation, inputVertexName, parameterAttributeName, filterEmpty)
    }


    // The output half: FlowOutput vertices' result names, in notation order.
    fun outputResultNames(
        graphNotation: GraphNotation,
        flowMainLocation: ObjectLocation,
        filterEmpty: Boolean = true
    ): List<String> {
        return signatureComponentNames(
            graphNotation, flowMainLocation, outputVertexName, resultAttributeName, filterEmpty)
    }


    private fun signatureComponentNames(
        graphNotation: GraphNotation,
        flowMainLocation: ObjectLocation,
        vertexTypeName: ObjectName,
        nameAttributeName: AttributeName,
        filterEmpty: Boolean
    ): List<String> {
        val verticesNotation = graphNotation
            .firstAttribute(flowMainLocation, verticesAttributeName)
            as? ListAttributeNotation
            ?: return listOf()

        // The `vertices` list entries are object references (the same resolution FlowMatrix uses),
        // so the list order — the vertex order everywhere else — is the signature order.
        return verticesNotation
            .values
            .mapNotNull { (it as? ScalarAttributeNotation)?.value }
            .mapNotNull { graphNotation.coalesce.locateOptional(ObjectReference.parse(it)) }
            .filter { vertexLocation ->
                graphNotation.inheritanceChain(vertexLocation).any { it.objectPath.name == vertexTypeName }
            }
            .map {
                (graphNotation.firstAttribute(it, nameAttributeName) as? ScalarAttributeNotation)?.value ?: ""
            }
            .filter { !filterEmpty || it.isNotEmpty() }
    }
}
