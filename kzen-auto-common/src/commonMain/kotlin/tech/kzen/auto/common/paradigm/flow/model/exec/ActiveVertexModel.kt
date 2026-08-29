package tech.kzen.auto.common.paradigm.flow.model.exec

import tech.kzen.lib.common.exec.data.value.DataValue

class ActiveVertexModel(
        var state: Any?,
        var message: DataValue?,
        val remainingBatch: MutableList<DataValue>,
        var streamHasNext: Boolean,
        var epoch: Int,

        // TODO: factor out to be visual-only for performance?
        var error: String?
) {
    fun hasNext(): Boolean {
        return remainingBatch.isNotEmpty() || streamHasNext
    }
}
