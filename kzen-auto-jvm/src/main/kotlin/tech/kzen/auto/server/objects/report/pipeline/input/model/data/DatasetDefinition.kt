package tech.kzen.auto.server.objects.report.pipeline.input.model.data


data class DatasetDefinition<T>(
    val items: List<FlatDataContentDefinition<T>>
): AutoCloseable {
    override fun close() {
        for (flatDataContentDefinition in items) {
            flatDataContentDefinition.processorDefinition.close()
        }
    }
}