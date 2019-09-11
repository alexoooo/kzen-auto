package tech.kzen.auto.common.service

import tech.kzen.lib.common.model.instance.ObjectInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner

class GraphInstanceManager(
        private val graphStructureManager: GraphStructureManager,
        private val graphDefiner: GraphDefiner,
        private val graphCreator: GraphCreator
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
    suspend fun get(objectLocation: ObjectLocation): ObjectInstance {
        val graphStructure = graphStructureManager.serverGraphStructure()

        val graphDefinition = graphDefiner.define(graphStructure)

        val objectGraph = graphCreator.createGraph(
                graphStructure, graphDefinition)

        return objectGraph[objectLocation]!!
    }
}