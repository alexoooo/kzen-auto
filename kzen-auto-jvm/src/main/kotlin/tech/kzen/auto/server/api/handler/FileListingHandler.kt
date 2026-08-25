package tech.kzen.auto.server.api.handler

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.util.data.DataListing
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.server.data.FileListingAction


class FileListingHandler(
    private val fileListingAction: FileListingAction
) {
    //-----------------------------------------------------------------------------------------------------------------
    // Document-agnostic directory listing (GET /file-listing?directory=...&filter=...): lists immediate
    // subdirectories first (regardless of filter), then filter-matching files. A file `directory` yields just that
    // file; a missing / non-directory path yields an empty list (FileListingAction). The reply also carries the
    // absolute directory that was read, since the request may name a relative one.
    fun fileListing(parameters: Parameters): DataListing {
        val directory: String = parameters.getParam(CommonRestApi.paramDirectory) { it }
        val filter: String = parameters.getParamOrNull(CommonRestApi.paramFilter) { it } ?: ""

        val listing = runBlocking {
            fileListingAction.browseListing(DataLocation.of(directory), filter)
        }

        return listing
    }
}
