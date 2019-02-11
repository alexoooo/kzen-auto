package tech.kzen.auto.common.exec

import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectName
import tech.kzen.lib.common.util.Digest


// TODO: use persistent data structure
data class ExecutionModel(
        val frames: MutableList<ExecutionFrame>
) {
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
