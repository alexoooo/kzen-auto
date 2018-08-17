package tech.kzen.auto.common.exec


data class ExecutionModel(
        val frames: MutableList<ExecutionFrame>
) {
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


    //-----------------------------------------------------------------------------------------------------------------
    fun next(): String? {
        if (frames.isEmpty()) {
            return null
        }

        val lastFrame = frames.last()

        for (e in lastFrame.values) {
            if (e.value == ExecutionStatus.Pending) {
                return e.key
            }
        }

        return null
    }
}
