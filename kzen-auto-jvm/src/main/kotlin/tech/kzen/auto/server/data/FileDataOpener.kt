package tech.kzen.auto.server.data

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.api.DataOpener
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.plugin.definition.ReportDefinition
import tech.kzen.auto.plugin.model.PluginCoordinate
import tech.kzen.auto.plugin.model.data.DataBlockBuffer
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.spec.DataEncodingSpec
import tech.kzen.auto.plugin.spec.TextEncodingSpec
import tech.kzen.auto.server.objects.plugin.PluginUtils.asPluginCoordinate
import tech.kzen.auto.server.objects.plugin.PluginUtils.asCommon
import tech.kzen.auto.server.objects.plugin.model.ClassLoaderHandle
import tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataHeaderDefinition
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.auto.server.objects.report.service.ReportUtils.asCommon
import tech.kzen.lib.platform.ClassName
import java.nio.charset.Charset


/**
 * Opens plain file refs through [FlatDataSource], the byte-stream seam; unlike
 * [tech.kzen.auto.common.data.api.DataSource], it neither owns nor resolves a configured query.
 */
class FileDataOpener(
    private val definitionRepository: ReportDefinitionRepository,
    private val schemaCache: SchemaCache
): DataOpener {
    companion object {
        // The payload every flat-file definition produces, and the type a Report's input selection declares by
        // default (`common-document.yaml` input.selection.dataType) — the axis format resolution selects along.
        // Internal rather than private so DataSourceActions offers the UI exactly the definitions this opener
        // would resolve among: one list, so what can be chosen and what can be read cannot drift apart.
        internal val flatRecordPayloadType = ClassName(FlatFileRecord::class.java.name)
    }


    override suspend fun open(context: DataContext, part: DataPart): DataCursor {
        var acquired: FileDataCursor? = null
        try {
            return context.blocking {
                openBlocking(part).also { acquired = it }
            }
        }
        catch (t: Throwable) {
            // A blocking dispatcher may complete acquisition concurrently with prompt cancellation of the
            // awaiting coroutine. Ownership has not reached the caller in that case, so close it here.
            try {
                acquired?.close()
            }
            catch (closeFailure: Throwable) {
                t.addSuppressed(closeFailure)
            }
            throw t
        }
    }


    override suspend fun inspectShape(context: DataContext, part: DataPart): DataShape? {
        return context.blocking {
            val spec = effectiveSpec(part)
            val key = SchemaCacheKey.of(part, spec.coordinate.asCommon(), spec.encoding.asCommon())
            key?.let(schemaCache::get) ?: inspectBlocking(spec).also { shape ->
                if (key != null) {
                    schemaCache.put(key, shape)
                }
            }
        }
    }


    private fun openBlocking(part: DataPart): FileDataCursor {
        val spec = effectiveSpec(part)
        val key = SchemaCacheKey.of(part, spec.coordinate.asCommon(), spec.encoding.asCommon())
        val cachedShape = key?.let(schemaCache::get)

        val handle = definitionRepository.classLoaderHandle(
            setOf(spec.coordinate), ClassLoaderUtils.dynamicParentClassLoader())
        try {
            val definition = definitionRepository.define(spec.coordinate, handle)
            val shape = cachedShape ?: inspect(spec, definition).also {
                if (key != null) {
                    schemaCache.put(key, it)
                }
            }
            return openCursor(spec, definition, shape, handle)
        }
        catch (t: Throwable) {
            handle.close()
            throw t
        }
    }


    private fun effectiveSpec(part: DataPart): EffectiveOpenSpec {
        val location = part.ref.asLocationOrNull()
            ?: throw IllegalArgumentException("Plain file reference expected: ${part.ref.display()}")
        val coordinate = part.format?.asPluginCoordinate() ?: inferCoordinate(location)
        require(coordinate in definitionRepository) { "Unknown data format: $coordinate" }
        val encoding = part.encoding?.asDataEncodingSpec()
            ?: definitionRepository.metadata(coordinate)?.reportDefinitionInfo?.dataEncoding
            ?: DataEncodingSpec.utf8
        return EffectiveOpenSpec(location, coordinate, encoding)
    }


    private fun inspectBlocking(spec: EffectiveOpenSpec): DataShape.Tabular {
        val handle = definitionRepository.classLoaderHandle(
            setOf(spec.coordinate), ClassLoaderUtils.dynamicParentClassLoader())
        return handle.use {
            inspect(spec, definitionRepository.define(spec.coordinate, it))
        }
    }


    private fun <T> inspect(
        spec: EffectiveOpenSpec,
        definition: ReportDefinition<T>
    ): DataShape.Tabular {
        val headerDefinition = FlatDataHeaderDefinition(
            FlatDataLocation(spec.location, spec.encoding), FileFlatDataSource.instance, definition)
        return DataShape.Tabular(ReportHeaderReader().extract(headerDefinition))
    }


    /**
     * The format to read a file as when none was chosen, via the same resolution Report has always used
     * ([ReportDefinitionRepository.find], as called by `ReportDocument.actionDefaultFormat`): prefer a definition
     * claiming the extension, otherwise the highest-priority definition that is not marked avoid — in the host
     * build, Text.
     *
     * This deliberately does not fail on an unrecognized extension. A local re-implementation used to, which made
     * selecting a `.md` (or any unclaimed extension) a dead end discoverable only by running the Job, while the
     * same file in a Report simply read as text. Reading it as one Text column is a defensible default AND a
     * visible one — the row's Format is right there under Details to change.
     */
    private fun inferCoordinate(location: DataLocation): PluginCoordinate {
        return definitionRepository
            .find(flatRecordPayloadType, location)
            .firstOrNull()
            ?.coordinate
            ?: throw IllegalArgumentException(
                "Unable to infer data format for '${location.asString()}'" +
                        " from extension '${location.innerExtension()}'" +
                        " (registered extensions: ${registeredExtensions()})")
    }


    private fun registeredExtensions(): String {
        val extensions = definitionRepository
            .listMetadata()
            .flatMap { it.reportDefinitionInfo.extensions }
            .distinct()
            .sorted()
        return if (extensions.isEmpty()) "none" else extensions.joinToString(", ")
    }


    private fun <T> openCursor(
        spec: EffectiveOpenSpec,
        definition: ReportDefinition<T>,
        shape: DataShape,
        handle: ClassLoaderHandle
    ): FileDataCursor {
        val tabular = shape as? DataShape.Tabular
            ?: throw IllegalStateException("File data opener requires a tabular shape, found $shape")
        val headerDefinition = FlatDataHeaderDefinition(
            FlatDataLocation(spec.location, spec.encoding), FileFlatDataSource.instance, definition)

        val contentChain = headerDefinition.openInputChain(
            DataBlockBuffer.defaultBytesSize)
        return FileDataCursor(contentChain, tabular, handle)
    }


    private fun CommonDataEncodingSpec.asDataEncodingSpec(): DataEncodingSpec {
        val text = textEncoding ?: return DataEncodingSpec.binary
        return DataEncodingSpec(TextEncodingSpec(Charset.forName(text.charsetName)))
    }


    private data class EffectiveOpenSpec(
        val location: DataLocation,
        val coordinate: PluginCoordinate,
        val encoding: DataEncodingSpec
    )
}
