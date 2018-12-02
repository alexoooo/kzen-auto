package tech.kzen.auto.common.exec

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
    fun rename(from: String, to: String): Boolean {
        var renamedAny = false

        for (frame in frames) {
            val renamed = frame.rename(from, to)

            renamedAny = renamedAny || renamed
        }

        return renamedAny
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun findLast(
            objectName: String
    ): ExecutionFrame? =
        frames.findLast {
            it.contains(objectName)
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
    fun next(): String? {
        if (frames.isEmpty() || containsStatus(ExecutionStatus.Running)) {
            return null
        }

        val lastFrame = frames.last()

        for (e in lastFrame.values) {
            if (e.value == ExecutionStatus.Failed) {
                return null
            }

            if (e.value == ExecutionStatus.Pending) {
                return e.key
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
                digest.addUtf8(e.key)
                digest.addInt(e.value.ordinal)
            }
        }

        return digest.digest()
    }
}
