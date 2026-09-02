package tech.kzen.auto.server.data

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.read.CursorAdoptionIdentity
import tech.kzen.auto.common.data.read.ReaderConfig
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.plugin.api.data.ReaderCapability
import tech.kzen.auto.plugin.api.data.ReaderInspectionRequest
import tech.kzen.auto.plugin.api.data.ReaderOpenRequest
import tech.kzen.auto.server.data.content.SequentialContentStack
import tech.kzen.auto.server.data.content.local.LocalDataContentProvider
import tech.kzen.auto.server.data.content.provider.DataContentProviderLookup
import tech.kzen.auto.server.data.read.OwnedReaderDataCursor
import tech.kzen.auto.server.data.read.ReaderCapabilityRegistry
import tech.kzen.auto.server.data.read.ReaderExecutionPolicies


class ConfiguredDataOpener(
    private val schemaCache: SchemaCache,
    private val readerCapabilities: ReaderCapabilityRegistry = ReaderCapabilityRegistry.withConfiguredReaders(),
    private val contentStack: SequentialContentStack = SequentialContentStack(
        DataContentProviderLookup(LocalDataContentProvider(), emptyMap())),
    private val policies: ReaderExecutionPolicies = ReaderExecutionPolicies()
): OperationalDataOpener {
    override suspend fun open(context: DataContext, part: DataPart): DataCursor {
        val resolved = resolve(part)
        val required = resolved.capability.requiredContent(resolved.config)
        val bytes = contentStack.openBytes(
            context,
            part.ref,
            part.expectedFingerprint,
            part.resolvedRead.contentCodings,
            required,
            policies.runContent,
            part.role.name)
        try {
            val cursor = resolved.capability.open(ReaderOpenRequest(
                part.ref.display(),
                part.role.name,
                resolved.config,
                bytes,
                policies.run))
            return OwnedReaderDataCursor(cursor, bytes, adoptionIdentity(part))
        }
        catch (failure: Throwable) {
            try {
                bytes.close()
            }
            catch (closeFailure: Throwable) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }


    override suspend fun inspectShape(context: DataContext, part: DataPart): DataShape {
        val key = SchemaCacheKey.of(part, policies.inspection)
        key?.let(schemaCache::get)?.let { return it }
        val resolved = resolve(part)
        val required = resolved.capability.requiredContent(resolved.config)
        val bytes = contentStack.openBytes(
            context,
            part.ref,
            part.expectedFingerprint,
            part.resolvedRead.contentCodings,
            required,
            policies.inspectionContent,
            part.role.name)
        var inspectionFailure: Throwable? = null
        val shape = try {
            resolved.capability.inspect(ReaderInspectionRequest(
                ReaderOpenRequest(
                    part.ref.display(),
                    part.role.name,
                    resolved.config,
                    bytes,
                    policies.run.copy(
                        maximumExpandedBytes = policies.inspection.maximumExpandedBytes,
                        timeoutMillis = policies.inspection.timeoutMillis)),
                requireNotNull(policies.inspection.maximumRecords).toLong()))
        }
        catch (failure: Throwable) {
            inspectionFailure = failure
            throw failure
        }
        finally {
            try {
                bytes.close()
            }
            catch (closeFailure: Throwable) {
                if (inspectionFailure == null) throw closeFailure
                inspectionFailure.addSuppressed(closeFailure)
            }
        }
        return shape.also { if (key != null) schemaCache.put(key, it) }
    }


    override fun adoptionIdentity(part: DataPart): CursorAdoptionIdentity =
        CursorAdoptionIdentity(part.digest(), policies.run.digest())


    private fun resolve(part: DataPart): ResolvedCapability {
        val capability = readerCapabilities.resolve(part.resolvedRead.reader)
        val config = readerCapabilities.decodeValidateCanonicalize(part.resolvedRead)
        return ResolvedCapability(capability, config)
    }

    private data class ResolvedCapability(
        val capability: ReaderCapability,
        val config: ReaderConfig
    )
}
