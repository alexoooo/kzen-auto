package tech.kzen.auto.server.data.write

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.server.data.write.delimited.ConfiguredDelimitedWriterCapability
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedTestFormats
import tech.kzen.lib.common.util.digest.Digest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue


class RecordWriterCapabilityRegistryTest {
    @Test
    fun aFormatIsWrittenByTheWriterItNamesWhenThatWriterIsRegistered() {
        val registry = RecordWriterCapabilityRegistry.withBuiltInWriters()

        assertSame(ConfiguredDelimitedWriterCapability, registry.writerFor(ConfiguredDelimitedTestFormats.csv()))
        assertSame(ConfiguredDelimitedWriterCapability, registry.writerFor(ConfiguredDelimitedTestFormats.tsv()))
        assertNull(registry.writerFor(ReadOnlyFormat(null)))
        assertNull(registry.writerFor(ReadOnlyFormat("tech.kzen.test/unregistered-writer")))
    }


    @Test
    fun twoWritersCannotShareAnIdentity() {
        val failure = assertFailsWith<IllegalStateException> {
            RecordWriterCapabilityRegistry(listOf(ConfiguredDelimitedWriterCapability, ConfiguredDelimitedWriterCapability))
        }

        assertTrue(failure.message!!.contains(ConfiguredDelimitedWriterCapability.identity), failure.message)
    }


    private class ReadOnlyFormat(
        override val writerCapabilityIdentity: String?
    ): ConfiguredRecordFormat {
        override val title = "Read only"
        override val extensions = emptyList<String>()
        override val catalogVisible = true

        @Deprecated("Use resolve(request) so contextual overrides and provenance are preserved")
        override fun resolvedRead(ref: DataRef): ResolvedReadSpec = error("Not read in this test")

        override fun declaredShape(): DataShape? = null

        override fun digest(sink: Digest.Sink) {
            sink.addUtf8(title)
        }
    }
}
