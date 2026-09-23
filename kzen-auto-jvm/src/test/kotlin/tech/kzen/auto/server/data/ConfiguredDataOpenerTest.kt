package tech.kzen.auto.server.data

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataSourceId
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.server.data.content.DirectDataContext
import tech.kzen.auto.server.data.content.FakeObjectStoreProvider
import tech.kzen.auto.server.data.content.SequentialContentStack
import tech.kzen.auto.server.data.content.fakeFingerprint
import tech.kzen.auto.server.data.content.local.LocalDataContentProvider
import tech.kzen.auto.server.data.content.provider.DataContentProviderLookup
import tech.kzen.auto.server.data.read.archive.ArchiveListingReadConfig
import tech.kzen.auto.server.data.read.archive.ArchiveListingReaderCapability
import tech.kzen.auto.server.util.WorkUtils
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse


class ConfiguredDataOpenerTest {
    @Test
    fun aFixedShapeIsInspectedWithoutReadingContentOrCaching() {
        // The archive listing's columns come from code alone: a cached copy could only go stale
        val source = DataSourceId("memory")
        val provider = FakeObjectStoreProvider("not a tar".encodeToByteArray(), fakeFingerprint("v1"))
        val root = Files.createTempDirectory("fixed-shape-inspection")
        val work = WorkUtils(root)
        val opener = ConfiguredDataOpener(
            SchemaCache(work),
            contentStack = SequentialContentStack(
                DataContentProviderLookup(LocalDataContentProvider(), mapOf(source to provider))))
        val part = DataPart(
            DataRole.main,
            DataRef(source, "data.tar"),
            fakeFingerprint("v1"),
            ResolvedReadSpec(
                ArchiveListingReaderCapability.identity,
                listOf(ContentCodingSpec.identity),
                ArchiveListingReaderCapability.encode(ArchiveListingReadConfig)))

        try {
            val shape = runBlocking { opener.inspectShape(DirectDataContext, part) }

            assertEquals(ArchiveListingReaderCapability.shape, shape)
            assertEquals(0, provider.acquireCount)
            assertFalse(Files.exists(work.resolve(SchemaCache.indexDirName)))
        }
        finally {
            root.toFile().deleteRecursively()
        }
    }
}
