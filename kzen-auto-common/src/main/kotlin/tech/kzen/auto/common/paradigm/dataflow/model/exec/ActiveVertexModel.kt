package tech.kzen.auto.common.paradigm.dataflow.model.exec


class ActiveVertexModel(
        var state: Any?,
        var message: Any?,
        val remainingBatch: MutableList<Any>,
        var streamHasNext: Boolean,
        var iterationCount: Long
) {
    fun hasNext(): Boolean {
        return remainingBatch.isNotEmpty() || streamHasNext
    }
}