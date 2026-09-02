package tech.kzen.auto.server.data.content

import kotlinx.coroutines.CancellationException
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.ContentCapabilityIdentity
import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.server.data.content.policy.ContentReadControl
import tech.kzen.auto.server.data.content.provider.DataContentDescriptor
import tech.kzen.auto.server.data.content.provider.DataContentHandle
import tech.kzen.auto.server.data.content.provider.DataContentProvider
import tech.kzen.lib.common.exec.TextExecutionValue


internal class FakeObjectStoreProvider(
    private val value: ByteArray,
    private val fingerprint: DataContentFingerprint,
    private val capabilities: Set<ContentCapabilityIdentity> = setOf(ContentCapabilityIdentity.sequentialBytes),
    private val cancelAfterAcquisition: Boolean = false,
    private val cancelOnRead: Boolean = false,
    private val acquisitionDelayMillis: Long = 0,
    private val readDelayMillis: Long = 0,
    private val maximumReadSize: Int = Int.MAX_VALUE,
    private val expectedId: String? = null
): DataContentProvider {
    var acquireCount = 0
        private set
    var readCount = 0
        private set
    var closeCount = 0
        private set


    override suspend fun describe(
        context: DataContext,
        ref: DataRef,
        control: ContentReadControl
    ): DataContentDescriptor {
        requireRef(ref)
        control.checkpoint()
        return DataContentDescriptor(ref, capabilities)
    }


    override suspend fun acquire(
        context: DataContext,
        ref: DataRef,
        control: ContentReadControl
    ): DataContentHandle {
        requireRef(ref)
        acquireCount++
        val content = FakeSequentialByteContent()
        val handle = DataContentHandle(DataContentDescriptor(ref, capabilities), fingerprint, content)
        try {
            if (acquisitionDelayMillis > 0) Thread.sleep(acquisitionDelayMillis)
            control.checkpoint()
            if (cancelAfterAcquisition) throw CancellationException("cancelled after acquisition")
            return handle
        }
        catch (t: Throwable) {
            handle.close()
            throw t
        }
    }


    private fun requireRef(ref: DataRef) {
        check(ref.source != null) { "Fake object-store refs must be sourced" }
        check(expectedId == null || ref.id == expectedId) { "Unknown opaque object '${ref.id}'" }
    }


    private inner class FakeSequentialByteContent: SequentialByteContent {
        private var offset = 0
        private var closed = false


        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            readCount++
            if (cancelOnRead && readCount > 1) throw CancellationException("cancelled during pull")
            if (this.offset == value.size) return -1
            if (readDelayMillis > 0) Thread.sleep(readDelayMillis)
            val count = minOf(length, maximumReadSize, value.size - this.offset)
            value.copyInto(buffer, offset, this.offset, this.offset + count)
            this.offset += count
            return count
        }


        override fun close() {
            if (closed) return
            closed = true
            closeCount++
        }
    }
}


internal fun fakeFingerprint(version: String): DataContentFingerprint {
    return DataContentFingerprint("test/fake-object-version-v1", TextExecutionValue(version))
}
