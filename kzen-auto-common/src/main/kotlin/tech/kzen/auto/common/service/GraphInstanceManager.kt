package tech.kzen.auto.common.service

import tech.kzen.lib.common.context.GraphCreator
import tech.kzen.lib.common.context.GraphDefiner
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.edit.NotationEvent


class GraphInstanceManager(
        private val graphStructureManager: GraphStructureManager
):
        GraphStructureManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
//    private val singletons = mutableMapOf<ObjectLocation, Any>()


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(
            graphStructure: GraphStructure,
            event: NotationEvent?
    ) {

    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun get(objectLocation: ObjectLocation): Any {
        val graphStructure = graphStructureManager.serverGraphStructure()

        val graphDefinition = GraphDefiner.define(graphStructure)

        val objectGraph = GraphCreator.createGraph(
                graphStructure, graphDefinition)

        return objectGraph.objects.get(objectLocation)!!
    }
}