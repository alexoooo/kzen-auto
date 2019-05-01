package tech.kzen.auto.common.service

import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.edit.NotationEvent


class GraphInstanceManager:
        GraphStructureManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    private val singletons = mutableMapOf<ObjectLocation, Any>()


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(
            graphStructure: GraphStructure,
            event: NotationEvent?
    ) {

    }


    //-----------------------------------------------------------------------------------------------------------------
    fun get(objectLocation: ObjectLocation): Any? {
        return singletons[objectLocation]
    }
}