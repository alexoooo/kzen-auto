package tech.kzen.auto.server.data.content.local

import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.ContentCapabilityIdentity
import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.common.util.data.FilePathJvm.toPath
import tech.kzen.auto.server.data.content.ContentSourceException
import tech.kzen.auto.server.data.content.policy.ContentReadControl
import tech.kzen.auto.server.data.content.provider.DataContentDescriptor
import tech.kzen.auto.server.data.content.provider.DataContentHandle
import tech.kzen.auto.server.data.content.provider.DataContentProvider
import kotlin.time.Instant
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.io.IOException


class LocalDataContentProvider: DataContentProvider {
    override suspend fun describe(
        context: DataContext,
        ref: DataRef,
        control: ContentReadControl
    ): DataContentDescriptor {
        control.checkpoint()
        requirePath(ref)
        return DataContentDescriptor(ref, setOf(ContentCapabilityIdentity.sequentialBytes))
    }


    override suspend fun acquire(
        context: DataContext,
        ref: DataRef,
        control: ContentReadControl
    ): DataContentHandle {
        var acquired: DataContentHandle? = null
        try {
            return context.blocking {
                acquireBlocking(ref, control).also { acquired = it }
            }
        }
        catch (t: Throwable) {
            try {
                acquired?.close()
            }
            catch (closeFailure: Throwable) {
                t.addSuppressed(closeFailure)
            }
            throw t
        }
    }


    private fun acquireBlocking(ref: DataRef, control: ContentReadControl): DataContentHandle {
        control.checkpoint()
        val path = requirePath(ref)
        val channel = try {
            Files.newByteChannel(path, StandardOpenOption.READ)
        }
        catch (e: IOException) {
            throw ContentSourceException(ref.display(), null, "Unable to open local content", e)
        }
        val content = ChannelSequentialByteContent(channel, control)
        try {
            val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
            control.checkpoint()
            return DataContentHandle(
                DataContentDescriptor(ref, setOf(ContentCapabilityIdentity.sequentialBytes)),
                observedFingerprint(ref, attributes),
                content)
        }
        catch (t: Throwable) {
            val failure = if (t is IOException) {
                ContentSourceException(ref.display(), null, "Unable to inspect local content", t)
            }
            else {
                t
            }
            try {
                content.close()
            }
            catch (closeFailure: Throwable) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }


    private fun requirePath(ref: DataRef) =
        ref.asLocationOrNull()?.filePath?.toPath()
            ?: throw ContentSourceException(ref.display(), null, "Local file reference expected")


    private fun observedFingerprint(ref: DataRef, attributes: BasicFileAttributes): DataContentFingerprint {
        val modified = Instant.fromEpochMilliseconds(attributes.lastModifiedTime().toMillis()).toString()
        val observedRef = DataRef(
            null,
            ref.id,
            mapOf(DataRef.sizeKey to attributes.size().toString(), DataRef.modifiedKey to modified))
        return DataContentFingerprint.localOrNull(observedRef)
            ?: error("Local fingerprint expected")
    }
}
