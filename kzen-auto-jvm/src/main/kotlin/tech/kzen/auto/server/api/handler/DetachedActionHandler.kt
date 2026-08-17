package tech.kzen.auto.server.api.handler

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.util.FormatUtils
import tech.kzen.auto.server.objects.job.service.JobWorkPool
import tech.kzen.auto.server.objects.report.exec.output.flat.IndexedCsvTable
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadResult
import tech.kzen.auto.server.service.exec.ModelDetachedExecutor
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.platform.DateTimeUtils
import java.nio.file.Files


class DetachedActionHandler(
    private val detachedExecutor: ModelDetachedExecutor,
    private val jobWorkPool: JobWorkPool
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun actionDetached(
        parameters: Parameters,
        body: ImmutableByteArray?
    ): ExecutionResult {
        val objectLocation = parameters.getObjectLocationParam()

        val detachedParams = mutableMapOf<String, List<String>>()
        for (e in parameters.entries()) {
            if (e.key == CommonRestApi.paramDocumentPath ||
                    e.key == CommonRestApi.paramObjectPath
            ) {
                continue
            }
            detachedParams[e.key] = e.value
        }

        val detachedRequest = ExecutionRequest(
            RequestParams(detachedParams), body)

        val execution: ExecutionResult = runBlocking {
            detachedExecutor.execute(
                objectLocation, detachedRequest)
        }

        return execution
    }


    fun actionDetachedDownload(
        parameters: Parameters,
        body: ImmutableByteArray?
    ): ExecutionDownloadResult {
        val objectLocation = parameters.getObjectLocationParam()

        val params = mutableMapOf<String, List<String>>()
        for (e in parameters.entries()) {
            if (e.key == CommonRestApi.paramDocumentPath ||
                    e.key == CommonRestApi.paramObjectPath) {
                continue
            }
            params[e.key] = e.value
        }

        val detachedRequest = ExecutionRequest(RequestParams(params), body)

        val execution: ExecutionDownloadResult = runBlocking {
            detachedExecutor.executeDownload(
                objectLocation, detachedRequest)
        }

        return execution
    }


    // Streaming download of a Job Explore Worker's PERSISTED result as table.csv — the Job analogue of Report's
    // detached download (actionDetachedDownload above). The Worker's IndexedCsvTable lives in a per-Worker
    // output dir keyed on its NOTATION identity (JobWorkPool.workerOutputDir), which SURVIVES the run settling
    // (last-run-wins), so this resolves it straight from path + object with NO live run — letting the report be
    // downloaded after the run ends. The object path both resolves the Worker's dir and names the file.
    fun jobDownload(parameters: Parameters): ExecutionDownloadResult {
        val workerLocation = parameters.getObjectLocationParam()

        val outputDir = jobWorkPool.workerOutputDir(workerLocation)
        val tablePath = IndexedCsvTable.tablePath(outputDir)

        // Domain-level guard, naming the Worker. Deliberately redundant with the transport-level existence
        // check in KzenAutoMain.respondDownload, which can only speak of a path.
        if (!Files.exists(tablePath)) {
            error("No downloadable result: $workerLocation")
        }

        val filenamePrefix = FormatUtils.sanitizeFilename(workerLocation.objectPath.name.value)
        val filename = filenamePrefix + "_" + DateTimeUtils.filenameTimestamp() + ".csv"

        return ExecutionDownloadResult.ofFile(tablePath, filename)
    }
}
