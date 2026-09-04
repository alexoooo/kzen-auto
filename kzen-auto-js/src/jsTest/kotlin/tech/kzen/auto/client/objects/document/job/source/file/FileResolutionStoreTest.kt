package tech.kzen.auto.client.objects.document.job.source.file

import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.model.DataManifest
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataResolveResult
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataSourceId
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class FileResolutionStoreTest {
    private val source = ObjectLocation.parse("job.yaml#main.workers/Input")
    private val entry = FileSelectionEntry(DataLocation.of("./orders.csv"), null, null)

    @Test
    fun rowEpochRejectsRemovedAndRecreatedWork() {
        val key = FileResolutionStore.Key.of(source, entry, "Automatic")
        val epochs = FileResolutionStore.Epochs()
        val removed = epochs.issue(key)

        epochs.invalidate(key)
        val recreated = epochs.issue(key)

        assertFalse(epochs.isCurrent(key, removed))
        assertTrue(epochs.isCurrent(key, recreated))
    }

    @Test
    fun configuredFormatBodyDigestParticipatesInTheKey() {
        val automatic = FileResolutionStore.Key.of(
            source, entry, "source-format:v1|row-format:digest-1")
        val changed = FileResolutionStore.Key.of(
            source, entry, "source-format:v1|row-format:digest-2")

        assertFalse(automatic == changed)
    }

    @Test
    fun onePartReplyPairsTheConcretePartAndProvenance() {
        val ref = DataRef(DataSourceId("file"), "orders.csv")
        val part = DataPart(
            DataRole.main,
            ref,
            null,
            ResolvedReadSpec(
                ReaderCapabilityIdentity("kzen", "delimited", "1"),
                emptyList(),
                MapExecutionValue(emptyMap())))
        val detail = FormatResolutionDetail(
            ref,
            "configured.yaml#Csv",
            "CSV",
            FormatSelectionKind.Automatic,
            FormatResolutionBasis.Extension,
            "The file extension and sample agree.")

        val resolution = FileResolutionStore.resolution(DataResolveResult(
            DataManifest(listOf(DataUnit(emptyMap(), listOf(part)))),
            emptyList(),
            listOf(detail)))

        assertEquals(DataManifest(listOf(DataUnit(emptyMap(), listOf(part)))), resolution.manifest)
        assertEquals(part, resolution.part)
        assertEquals(detail, resolution.detail)
    }
}
