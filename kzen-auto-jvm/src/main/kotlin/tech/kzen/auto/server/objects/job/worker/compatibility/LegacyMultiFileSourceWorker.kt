package tech.kzen.auto.server.objects.job.worker.compatibility

import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.objects.datasource.FileDataSource
import tech.kzen.auto.server.objects.job.worker.data.DataReadCore
import tech.kzen.auto.server.objects.job.worker.data.InlineDataSourceWorker
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.util.digest.Digest


@Reflect
class LegacyMultiFileSourceWorker(
    output: ChannelOutput<DataValue>,
    paths: List<String>,
    delimiter: String,
    header: Boolean,
    selfLocation: ObjectLocation,
    @Service openerLookup: DataOpenerLookup,
    @Service fileListingAction: FileListingAction
): InlineDataSourceWorker(
    output,
    "items",
    "",
    "ignore",
    selfLocation,
    openerLookup,
    DataReadCore.schemaSuperset,
    FileDataSource(
        "",
        "",
        paths.map { mapOf(FileSelectionEntry.locationKey to it) },
        compatibilityDelimitedFormat(delimiter, header),
        "",
        FileDataSource.missingFail,
        fileListingAction),
    Digest.build {
        addInt(paths.size)
        paths.forEach(::addUtf8)
        addUtf8(delimiter)
        addBoolean(header)
    })
