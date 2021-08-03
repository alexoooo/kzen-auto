package tech.kzen.auto.client.objects.document.pipeline.input.browse

import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.common.objects.document.pipeline.PipelineConventions
import tech.kzen.auto.common.objects.document.report.listing.InputBrowserInfo
import tech.kzen.auto.common.objects.document.report.spec.input.InputSpec
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.store.MirroredGraphError


object InputBrowserEndpoint {
    suspend fun browse(mainLocation: ObjectLocation): ClientResult<InputBrowserInfo> {
        val result = ClientContext.restClient.performDetached(
            mainLocation,
            PipelineConventions.actionParameter to PipelineConventions.actionBrowseFiles)

        return when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as Map<String, Any>

                val inputBrowserInfo = InputBrowserInfo.ofCollection(resultValue)
                ClientResult.ofSuccess(inputBrowserInfo)
            }

            is ExecutionFailure ->
                ClientResult.ofError(result.errorMessage)
        }
    }


//    fun selectDirAsync(mainLocation: ObjectLocation, dir: DataLocation, callback: (String?) -> Unit) {
//        async {
//            val error = selectDir(mainLocation, dir)
//            callback(error)
//        }
//    }


    suspend fun selectDir(mainLocation: ObjectLocation, dir: DataLocation): String? {
        val command = InputSpec.browseCommand(mainLocation, dir)

        @Suppress("MoveVariableDeclarationIntoWhen")
        val result = ClientContext.mirroredGraphStore.apply(command)

        return (result as? MirroredGraphError)?.error?.message
    }
}