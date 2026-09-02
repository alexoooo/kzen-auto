package tech.kzen.auto.server.objects.job.worker.data

import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.file.FileSelectionSpec
import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.objects.datasource.FileDataSource
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.exec.data.value.DataValue


@Reflect
class FileSourceWorker(
    output: ChannelOutput<DataValue>,
    directory: String,
    filter: String,
    files: List<Map<String, String>>,
    format: ConfiguredRecordFormat,
    groupPattern: String,
    missing: String,
    emit: String,
    role: String,
    attributes: String,
    selfLocation: ObjectLocation,
    @Service openerLookup: DataOpenerLookup,
    @Service fileListingAction: FileListingAction,
    schemaMode: String = DataReadCore.schemaSuperset
): InlineDataSourceWorker(
    output, emit, role, attributes, selfLocation, openerLookup, schemaMode,
    FileDataSource(
        directory, filter, files, format, groupPattern, missing, fileListingAction),
    compatibilityKey(directory, filter, files, format, groupPattern, missing)
) {
    companion object {
        internal fun compatibilityKey(
            directory: String,
            filter: String,
            files: List<Map<String, String>>,
            format: ConfiguredRecordFormat,
            groupPattern: String,
            missing: String,
        ): Digest = Digest.build {
            addUtf8(directory)
            addUtf8(filter)
            addDigestible(FileSelectionSpec(files.map(FileSelectionEntry::ofCollection)))
            addDigestible(format)
            addUtf8(groupPattern)
            addUtf8(missing)
        }
    }
}
