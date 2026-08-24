package tech.kzen.auto.server.api.handler

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.auto.server.data.FileListingAction


class FileListingHandler(
    private val fileListingAction: FileListingAction
) {
    //-----------------------------------------------------------------------------------------------------------------
    // Document-agnostic directory listing (GET /file-listing?directory=...&filter=...): lists the immediate
    // children (files + subdirectories) of `directory` matching `filter` via the reused FileListingAction, each
    // as its DataLocationInfo collection. The Job MultiFileInputEditor browses input files with it. A file
    // `directory` yields just that file; a missing / non-directory path yields an empty list (FileListingAction).
    fun fileListing(parameters: Parameters): List<DataLocationInfo> {
        val directory: String = parameters.getParam(CommonRestApi.paramDirectory) { it }
        val filter: String = parameters.getParamOrNull(CommonRestApi.paramFilter) { it } ?: ""

        val listing = runBlocking {
            fileListingAction.scanInfo(DataLocation.of(directory), filter)
        }

        return listing
    }
}
