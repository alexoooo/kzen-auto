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
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
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
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

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
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val objectLocation = ObjectLocation(documentPath, objectPath)

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
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val objectPath: ObjectPath = parameters.getParam(
            CommonRestApi.paramObjectPath, ObjectPath::parse)

        val workerLocation = ObjectLocation(documentPath, objectPath)

        val outputDir = jobWorkPool.workerOutputDir(workerLocation)
        val tablePath = outputDir.resolve(IndexedCsvTable.tableFile)
        if (!Files.exists(tablePath)) {
            error("No downloadable result: $workerLocation")
        }

        val filenamePrefix = FormatUtils.sanitizeFilename(objectPath.name.value)
        val filename = filenamePrefix + "_" + DateTimeUtils.filenameTimestamp() + ".csv"

        return ExecutionDownloadResult(
            IndexedCsvTable.downloadCsvOffline(outputDir),
            filename,
            "text/csv")
    }
}
