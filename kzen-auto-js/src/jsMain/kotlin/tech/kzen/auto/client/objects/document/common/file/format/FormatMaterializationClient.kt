package tech.kzen.auto.client.objects.document.common.file.format

import kotlinx.serialization.encodeToString
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.clientJson
import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.format.FormatMaterializationActionRequest
import tech.kzen.auto.common.data.format.FormatMaterializationActionResult
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.location.ObjectLocation


fun interface FormatMaterializationClient {
    suspend fun materialize(
        source: ObjectLocation,
        entry: FileSelectionEntry,
        request: FormatMaterializationActionRequest
    ): FormatMaterializationActionResult
}


class RestFormatMaterializationClient(
    private val restClient: ClientRestApi
): FormatMaterializationClient {
    override suspend fun materialize(
        source: ObjectLocation,
        entry: FileSelectionEntry,
        request: FormatMaterializationActionRequest
    ): FormatMaterializationActionResult {
        val execution = restClient.performDetached(
            DataSourceConventions.dataSourceActionsLocation,
            clientJson.encodeToString(request).encodeToByteArray(),
            DataSourceConventions.sourceParameter to source.asString(),
            DataSourceConventions.actionParameter to DataSourceConventions.materializeFormatAction,
            DataSourceConventions.locationParameter to entry.location.asString(),
            DataSourceConventions.formatParameter to entry.format?.asString().orEmpty(),
            DataSourceConventions.encodingParameter to entry.encoding?.asString().orEmpty())
        return when (execution) {
            is ExecutionSuccess -> FormatMaterializationActionResult.ofExecutionValue(execution.value)
            is ExecutionFailure -> throw IllegalArgumentException(execution.errorMessage)
        }
    }
}
