package tech.kzen.auto.common.exec


data class ExecutionModel(
        val frames: MutableList<ExecutionFrame>
) {
    fun rename(from: String, to: String): ExecutionModel {
        val renamed = mutableListOf<ExecutionFrame>()

        for (frame in frames) {
            val renamedFrame = frame.rename(from, to)
            renamed.add(renamedFrame)
        }

        return ExecutionModel(renamed)
    }
}
