package tech.kzen.auto.common.paradigm.dataflow.model.exec

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.persistentMapOf
import tech.kzen.lib.platform.collect.toPersistentMap


data class VisualDataflowModel(
        val vertices: PersistentMap<ObjectLocation, VisualVertexModel>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = VisualDataflowModel(persistentMapOf())


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
                collection: Map<String, Any>
        ): VisualDataflowModel {
            return VisualDataflowModel(collection
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
    ): VisualDataflowModel {
        return VisualDataflowModel(
                vertices.put(vertexLocation, newModel))
    }


    fun remove(
            objectLocation: ObjectLocation
    ): VisualDataflowModel {
        return VisualDataflowModel(
                vertices.remove(objectLocation))
    }


    fun rename(from: ObjectLocation, newName: ObjectName): VisualDataflowModel {
        val state = vertices[from]
                ?: return this

        val newNamePath = from.objectPath.copy(name = newName)
        val newNameLocation = from.copy(objectPath = newNamePath)

        val removedAtOldName = vertices.remove(from)
        val addedAtNewName = removedAtOldName.put(newNameLocation, state)

        return VisualDataflowModel(addedAtNewName)
    }


    @Suppress("UNUSED_PARAMETER")
    fun move(from: DocumentPath, newPath: DocumentPath): VisualDataflowModel {
        return VisualDataflowModel(vertices.mapKeys {
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