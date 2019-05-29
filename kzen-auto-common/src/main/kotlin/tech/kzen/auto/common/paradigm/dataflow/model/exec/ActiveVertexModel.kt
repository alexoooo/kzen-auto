package tech.kzen.auto.common.paradigm.dataflow.model.exec


class ActiveVertexModel(
        var state: Any?,
        var message: Any?,
        val remainingBatch: MutableList<Any>,
        var streamHasNext: Boolean,
        var epoch: Long,

        // TODO: consider factor out to be visual-only for performance
        var error: String?
) {
    fun hasNext(): Boolean {
        return remainingBatch.isNotEmpty() || streamHasNext
    }
}