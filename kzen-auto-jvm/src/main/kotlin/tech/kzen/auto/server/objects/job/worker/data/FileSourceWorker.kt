package tech.kzen.auto.server.objects.job.worker.data

import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.file.FileSelectionSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.objects.data.schema.DataSchemaDocument
import tech.kzen.auto.server.objects.datasource.FileDataSource
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.util.digest.Digest


@Reflect
class FileSourceWorker(
    output: ChannelOutput<Any?>,
    directory: String,
    filter: String,
    files: List<Map<String, String>>,
    format: String,
    encoding: String,
    groupPattern: String,
    missing: String,
    schema: DataSchemaDocument?,
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
        directory, filter, files, format, encoding, groupPattern, missing, fileListingAction, schema),
    compatibilityKey(directory, filter, files, format, encoding, groupPattern, missing, schema)
) {
    companion object {
        internal fun compatibilityKey(
            directory: String,
            filter: String,
            files: List<Map<String, String>>,
            format: String,
            encoding: String,
            groupPattern: String,
            missing: String,
            schema: DataSchemaDocument?
        ): Digest = Digest.build {
            addUtf8(directory)
            addUtf8(filter)
            addDigestible(FileSelectionSpec(files.map(FileSelectionEntry::ofCollection)))
            addUtf8(format)
            addUtf8(encoding)
            addUtf8(groupPattern)
            addUtf8(missing)
            addDigestibleNullable(schema?.shape()?.header)
        }
    }
}
