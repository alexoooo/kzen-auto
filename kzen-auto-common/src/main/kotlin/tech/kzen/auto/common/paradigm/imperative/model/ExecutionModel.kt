package tech.kzen.auto.common.paradigm.imperative.model

import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectName
import tech.kzen.lib.common.util.Digest


// TODO: use persistent data structure
data class ExecutionModel(
        val frames: MutableList<ExecutionFrame>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun toCollection(model: ExecutionModel): List<Map<String, Any>> {
            return model
                    .frames
                    .map { ExecutionFrame.toCollection(it) }
        }


        fun fromCollection(
                collection: List<Map<String, Any>>
        ): ExecutionModel {
            return ExecutionModel(collection
                    .map { ExecutionFrame.fromCollection(it) }
                    .toMutableList())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun remove(objectLocation: ObjectLocation): Boolean {
        var renamedAny = false

        for (frame in frames) {
            if (frame.path != objectLocation.documentPath) {
                continue
            }

            val renamed = frame.remove(objectLocation.objectPath)

            renamedAny = renamedAny || renamed
        }

        return renamedAny
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun rename(from: ObjectLocation, newName: ObjectName): Boolean {
        var renamedAny = false

        for (frame in frames) {
            if (frame.path != from.documentPath) {
                continue
            }

            val renamed = frame.rename(from.objectPath, newName)

            renamedAny = renamedAny || renamed
        }

        return renamedAny
    }


    fun add(objectLocation: ObjectLocation): Boolean {
        for (frame in frames) {
            if (frame.path != objectLocation.documentPath) {
                continue
            }

            // TODO: just frame.add without checking contains?
            if (! frame.contains(objectLocation.objectPath)) {
                frame.add(objectLocation.objectPath)
            }
            return true
        }

        return false
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun findLast(
            objectLocation: ObjectLocation
    ): ExecutionFrame? =
        frames.findLast {
            it.path == objectLocation.documentPath &&
                    it.contains(objectLocation.objectPath)
        }


    fun containsStatus(status: ExecutionPhase): Boolean {
        for (frame in frames) {
            if (frame.states.values.find { it.phase() == status } != null) {
                return true
            }
        }
        return false
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun next(): ObjectLocation? {
        if (frames.isEmpty() || containsStatus(ExecutionPhase.Running)) {
            return null
        }

        val lastFrame = frames.last()

        for (e in lastFrame.states) {
            if (e.value.phase() == ExecutionPhase.Error) {
                return null
            }

            if (e.value.phase() == ExecutionPhase.Pending) {
                return ObjectLocation(lastFrame.path, e.key)
            }
        }

        return null
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun digest(): Digest {
        val digest = Digest.Streaming()

        digest.addInt(frames.size)

        for (frames in frames) {
            for (e in frames.states) {
                digest.addUtf8(e.key.asString())
                digest.addDigest(e.value.digest())
            }
        }

        return digest.digest()
    }
}
