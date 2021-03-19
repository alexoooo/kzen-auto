package tech.kzen.auto.server.objects.report.pipeline.input.model.data


data class DatasetDefinition<T>(
    val items: List<FlatDataContentDefinition<T>>
)