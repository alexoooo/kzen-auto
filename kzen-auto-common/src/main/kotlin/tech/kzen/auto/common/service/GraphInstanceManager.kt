package tech.kzen.auto.common.service

import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.instance.ObjectInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.store.LocalGraphStore


class GraphInstanceManager(
        private val graphStore: LocalGraphStore,
        private val graphCreator: GraphCreator)
{
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun get(objectLocation: ObjectLocation): ObjectInstance {
        val graphDefinition = graphStore
                .graphDefinition()
                .filterDefinitions(AutoConventions.serverAllowed)

        val objectGraph = graphCreator
                .createGraph(graphDefinition)

        return objectGraph[objectLocation]!!
    }
}