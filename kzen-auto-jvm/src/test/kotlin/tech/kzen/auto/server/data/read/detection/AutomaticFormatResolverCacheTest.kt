package tech.kzen.auto.server.data.read.detection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.detection.DetectionPolicy
import tech.kzen.auto.common.data.format.detection.NormalizedFormatHints
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataSourceId
import tech.kzen.auto.common.data.read.ContentCapabilityIdentity
import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.data.content.DirectDataContext
import tech.kzen.auto.server.data.content.SequentialByteContent
import tech.kzen.auto.server.data.content.SequentialContentStack
import tech.kzen.auto.server.data.content.fakeFingerprint
import tech.kzen.auto.server.data.content.local.LocalDataContentProvider
import tech.kzen.auto.server.data.content.policy.ContentReadControl
import tech.kzen.auto.server.data.content.provider.DataContentDescriptor
import tech.kzen.auto.server.data.content.provider.DataContentHandle
import tech.kzen.auto.server.data.content.provider.DataContentProvider
import tech.kzen.auto.server.data.content.provider.DataContentProviderLookup
import tech.kzen.auto.server.data.read.SuccessfulFormatResolutionCache
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class AutomaticFormatResolverCacheTest {
    @Test
    fun warmHitReadsNothingAndFingerprintAndHintsInvalidateTheRealCache() = runBlocking {
        val context = KzenAutoContext.forTest()
        try {
            val fixture = fixture(context)
            val first = fixture.resolver.resolve(fixture.request("csv"))
            val acquisitionsAfterCold = fixture.provider.acquireCount
            val readsAfterCold = fixture.provider.readCount

            assertEquals(first, fixture.resolver.resolve(fixture.request("csv")))
            assertEquals(acquisitionsAfterCold, fixture.provider.acquireCount)
            assertEquals(readsAfterCold, fixture.provider.readCount)

            fixture.provider.fingerprint = fakeFingerprint("cache-v2")
            fixture.resolver.resolve(fixture.request("csv"))
            val readsAfterFingerprintChange = fixture.provider.readCount
            assertTrue(readsAfterFingerprintChange > readsAfterCold)

            fixture.resolver.resolve(fixture.request("txt"))
            assertTrue(fixture.provider.readCount > readsAfterFingerprintChange)
            assertEquals(fixture.provider.acquireCount, fixture.provider.closeCount)
        }
        finally {
            context.close()
        }
    }


    @Test
    fun acquisitionCancellationAndReadFailureAreClosedAndNeverCached() = runBlocking {
        val context = KzenAutoContext.forTest()
        try {
            val fixture = fixture(context)
            fixture.provider.cancelNextAcquisition = true
            assertFailsWith<CancellationException> {
                fixture.resolver.resolve(fixture.request("csv"))
            }
            assertEquals(fixture.provider.acquireCount, fixture.provider.closeCount)

            fixture.provider.failNextRead = true
            assertFailsWith<IllegalStateException> {
                fixture.resolver.resolve(fixture.request("csv"))
            }
            assertEquals(fixture.provider.acquireCount, fixture.provider.closeCount)

            val recovered = fixture.resolver.resolve(fixture.request("csv"))
            val readsAfterRecovery = fixture.provider.readCount
            assertEquals(recovered, fixture.resolver.resolve(fixture.request("csv")))
            assertEquals(readsAfterRecovery, fixture.provider.readCount)
            assertEquals(fixture.provider.acquireCount, fixture.provider.closeCount)
        }
        finally {
            context.close()
        }
    }


    @Test
    fun policyChangeInvalidatesAnOtherwiseWarmResolution() = runBlocking {
        val context = KzenAutoContext.forTest()
        try {
            val cache = SuccessfulFormatResolutionCache()
            val base = DetectionPolicy.default(context.configuredRecordFormatRegistry.hintMetadata())
            val fixture = fixture(context, cache, base)
            fixture.resolver.resolve(fixture.request("csv"))
            val readsAfterFirstPolicy = fixture.provider.readCount

            val changedPolicyResolver = AutomaticFormatResolver(
                context.configuredRecordFormatRegistry,
                context.readerCapabilityRegistry,
                fixture.acquirer,
                cache,
                base.copy(maximumLogicalRecords = base.maximumLogicalRecords - 1))
            changedPolicyResolver.resolve(fixture.request("csv"))

            assertTrue(fixture.provider.readCount > readsAfterFirstPolicy)
            assertEquals(fixture.provider.acquireCount, fixture.provider.closeCount)
        }
        finally {
            context.close()
        }
    }


    private fun fixture(
        context: KzenAutoContext,
        cache: SuccessfulFormatResolutionCache = SuccessfulFormatResolutionCache(),
        policy: DetectionPolicy? = null
    ): Fixture {
        val source = DataSourceId("mutable-cache-provider")
        val provider = MutableProvider(
            "name,count\nalice,1\n".encodeToByteArray(),
            fakeFingerprint("cache-v1"))
        val stack = SequentialContentStack(DataContentProviderLookup(
            LocalDataContentProvider(), mapOf(source to provider)))
        val acquirer = DetectionSampleAcquirer(stack)
        return Fixture(
            AutomaticFormatResolver(
                context.configuredRecordFormatRegistry,
                context.readerCapabilityRegistry,
                acquirer,
                cache,
                policy),
            acquirer,
            provider,
            source)
    }


    private data class Fixture(
        val resolver: AutomaticFormatResolver,
        val acquirer: DetectionSampleAcquirer,
        val provider: MutableProvider,
        val source: DataSourceId
    ) {
        fun request(extension: String): FormatResolutionRequest = FormatResolutionRequest(
            DirectDataContext,
            DataRef(source, "orders.csv"),
            provider.fingerprint,
            NormalizedFormatHints.of(filenameExtension = extension),
            null)
    }


    private class MutableProvider(
        private val bytes: ByteArray,
        var fingerprint: DataContentFingerprint
    ): DataContentProvider {
        var acquireCount = 0
            private set
        var readCount = 0
            private set
        var closeCount = 0
            private set
        var cancelNextAcquisition = false
        var failNextRead = false


        override suspend fun describe(
            context: DataContext,
            ref: DataRef,
            control: ContentReadControl
        ): DataContentDescriptor {
            control.checkpoint()
            return descriptor(ref)
        }


        override suspend fun acquire(
            context: DataContext,
            ref: DataRef,
            control: ContentReadControl
        ): DataContentHandle {
            acquireCount += 1
            val content = Content()
            val handle = DataContentHandle(descriptor(ref), fingerprint, content)
            try {
                control.checkpoint()
                if (cancelNextAcquisition) {
                    cancelNextAcquisition = false
                    throw CancellationException("test acquisition cancellation")
                }
                return handle
            }
            catch (failure: Throwable) {
                handle.close()
                throw failure
            }
        }


        private fun descriptor(ref: DataRef) = DataContentDescriptor(
            ref, setOf(ContentCapabilityIdentity.sequentialBytes))


        private inner class Content: SequentialByteContent {
            private var offset = 0
            private var closed = false


            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                readCount += 1
                if (failNextRead) {
                    failNextRead = false
                    throw IllegalStateException("test operational read failure")
                }
                if (this.offset == bytes.size) return -1
                val count = minOf(length, bytes.size - this.offset)
                bytes.copyInto(buffer, offset, this.offset, this.offset + count)
                this.offset += count
                return count
            }


            override fun close() {
                if (closed) return
                closed = true
                closeCount += 1
            }
        }
    }
}
