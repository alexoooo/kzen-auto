package tech.kzen.auto.common.paradigm.dataflow.model.exec

import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.persistentMapOf
import tech.kzen.lib.platform.collect.toPersistentMap


data class VisualDataflowModel(
        val vertices: PersistentMap<ObjectLocation, VisualVertexModel>//,
//        val dataflowDag: DataflowDag
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = VisualDataflowModel(persistentMapOf()/*, DataflowDag.of(VertexMatrix.empty)*/)


        fun toCollection(
                model: VisualDataflowModel
        ): Map<String, Any> {
            return model
                    .vertices
                    .mapKeys {
                        it.key.asString()
                    }
                    .mapValues {
                        VisualVertexModel.toCollection(it.value)
                    }
        }


        @Suppress("UNCHECKED_CAST")
        fun fromCollection(
                collection: Map<String, Any>/*,
                dataflowDag: DataflowDag*/
        ): VisualDataflowModel {
            return VisualDataflowModel(collection
                    .map {
                        ObjectLocation.parse(it.key) to
                                VisualVertexModel.fromCollection(it.value as Map<String, Any?>)
                    }
                    .toPersistentMap()/*,
                    dataflowDag*/
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun put(
            vertexLocation: ObjectLocation,
            newModel: VisualVertexModel
    ): VisualDataflowModel {
        return VisualDataflowModel(
                vertices.put(vertexLocation, newModel)/*,
                dataflowDag*/)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun digest(): Digest {
        val digest = Digest.Streaming()

        digest.addInt(vertices.size)

        for ((path, model) in vertices) {
            digest.addUtf8(path.asString())
            digest.addDigest(model.digest())
        }

        return digest.digest()
    }
}