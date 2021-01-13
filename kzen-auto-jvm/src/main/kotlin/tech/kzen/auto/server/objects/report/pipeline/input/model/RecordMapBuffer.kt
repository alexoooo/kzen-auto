package tech.kzen.auto.server.objects.report.pipeline.input.model


data class RecordMapBuffer(
    val header: RecordHeaderBuffer = RecordHeaderBuffer(),
    val item: RecordItemBuffer = RecordItemBuffer(0, 0)
) {
    fun clear() {
        header.value = RecordHeader.empty
        item.clear()
    }
}