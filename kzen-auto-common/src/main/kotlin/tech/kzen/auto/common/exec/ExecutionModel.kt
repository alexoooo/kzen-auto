package tech.kzen.auto.common.exec

import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectName
import tech.kzen.lib.common.util.Digest


// TODO: should use persistent data structure
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


        fun fromCollection(collection: List<Map<String, Any>>): ExecutionModel {
            return ExecutionModel(collection
                    .map { ExecutionFrame.fromCollection(it) }
                    .toMutableList())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun rename(from: ObjectLocation, newName: ObjectName): Boolean {
        var renamedAny = false

        for (frame in frames) {
            if (frame.path != from.bundlePath) {
                continue
            }

            val renamed = frame.rename(from.objectPath, newName)

            renamedAny = renamedAny || renamed
        }

        return renamedAny
    }


    fun add(objectLocation: ObjectLocation): Boolean {
        for (frame in frames) {
            if (frame.path != objectLocation.bundlePath) {
                continue
            }

            frame.add(objectLocation.objectPath)
            return true
        }

        return false
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun findLast(
            objectLocation: ObjectLocation
    ): ExecutionFrame? =
        frames.findLast {
            it.path == objectLocation.bundlePath &&
                    it.contains(objectLocation.objectPath)
        }


    fun containsStatus(status: ExecutionStatus): Boolean {
        for (frame in frames) {
            if (frame.values.containsValue(status)) {
                return true
            }
        }
        return false
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun next(): ObjectLocation? {
        if (frames.isEmpty() || containsStatus(ExecutionStatus.Running)) {
            return null
        }

        val lastFrame = frames.last()

        for (e in lastFrame.values) {
            if (e.value == ExecutionStatus.Failed) {
                return null
            }

            if (e.value == ExecutionStatus.Pending) {
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
            for (e in frames.values) {
                digest.addUtf8(e.key.asString())
                digest.addInt(e.value.ordinal)
            }
        }

        return digest.digest()
    }
}
