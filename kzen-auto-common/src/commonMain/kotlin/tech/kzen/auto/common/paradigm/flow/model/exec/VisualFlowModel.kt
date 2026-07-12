package tech.kzen.auto.common.paradigm.flow.model.exec

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.persistentMapOf
import tech.kzen.lib.platform.collect.toPersistentMap


data class VisualFlowModel(
    val vertices: PersistentMap<ObjectLocation, VisualVertexModel>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = VisualFlowModel(persistentMapOf())


        fun toJsonCollection(
                model: VisualFlowModel
        ): Map<String, Any> {
            return model
                .vertices
                .mapKeys {
                    it.key.asString()
                }
                .mapValues {
                    VisualVertexModel.toJsonCollection(it.value)
                }
        }


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(
            collection: Map<String, Any>
        ): VisualFlowModel {
            return VisualFlowModel(collection
                .map {
                    ObjectLocation.parse(it.key) to
                        VisualVertexModel.fromCollection(it.value as Map<String, Any?>)
                }
                .toPersistentMap()
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isRunning(): Boolean {
        return vertices.values.any { it.running }
    }


    fun running(): ObjectLocation? {
        return vertices.filter { it.value.running }.keys.firstOrNull()
    }
}
