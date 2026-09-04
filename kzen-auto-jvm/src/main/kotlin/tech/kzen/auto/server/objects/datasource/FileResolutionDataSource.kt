package tech.kzen.auto.server.objects.datasource

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.api.DataSource
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.model.DataResolveResult


/** Optional source capability used by detached per-file preview and authoring actions. */
interface FileResolutionDataSource: DataSource {
    suspend fun resolveFile(context: DataContext, entry: FileSelectionEntry): DataResolveResult
}
