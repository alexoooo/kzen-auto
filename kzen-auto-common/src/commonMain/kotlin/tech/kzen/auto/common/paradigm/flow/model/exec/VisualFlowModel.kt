package tech.kzen.auto.common.paradigm.flow.model.exec

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.util.digest.Digest
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
    fun put(
        vertexLocation: ObjectLocation,
        newModel: VisualVertexModel
    ): VisualFlowModel {
        return VisualFlowModel(
            vertices.put(vertexLocation, newModel))
    }


    fun remove(
        objectLocation: ObjectLocation
    ): VisualFlowModel {
        return VisualFlowModel(
            vertices.remove(objectLocation))
    }


    fun rename(from: ObjectLocation, newName: ObjectName): VisualFlowModel {
        val state = vertices[from]
            ?: return this

        val newNamePath = from.objectPath.copy(name = newName)
        val newNameLocation = from.copy(objectPath = newNamePath)

        val removedAtOldName = vertices.remove(from)
        val addedAtNewName = removedAtOldName.put(newNameLocation, state)

        return VisualFlowModel(addedAtNewName)
    }


    @Suppress("UNUSED_PARAMETER")
    fun move(from: DocumentPath, newPath: DocumentPath): VisualFlowModel {
        return VisualFlowModel(vertices.mapKeys {
            it.key.copy(documentPath = newPath)
        }.toPersistentMap())
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isInProgress(): Boolean {
        return vertices.values.any { it.epoch > 0 }
    }


    fun isRunning(): Boolean {
        return vertices.values.any { it.running }
    }


    fun running(): ObjectLocation? {
        return vertices.filter { it.value.running }.keys.firstOrNull()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun digest(): Digest {
        val digest = Digest.Builder()

        digest.addInt(vertices.size)

        for ((path, model) in vertices) {
            digest.addUtf8(path.asString())
            digest.addDigest(model.digest())
        }

        return digest.digest()
    }
}