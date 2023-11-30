package tech.kzen.auto.common.service

import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.instance.ObjectInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.store.LocalGraphStore


// TODO: one instance per execution frame (Graph)
class GraphInstanceCreator(
    private val graphStore: LocalGraphStore,
    private val graphCreator: GraphCreator
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun create(objectLocation: ObjectLocation): ObjectInstance {
        val graphDefinition = graphStore
            .graphDefinition()
            .transitiveSuccessful
            .filterDefinitions(AutoConventions.serverAllowed)

        val objectGraph = graphCreator
            .createGraph(graphDefinition)

        return objectGraph[objectLocation]!!
    }
}