package tech.kzen.auto.server.data.read.detection

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.detection.FormatHintMetadata
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.server.data.read.archive.ArchiveListingReaderCapability
import tech.kzen.auto.server.objects.datasource.format.ArchiveListingFormat
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedTestFormats
import tech.kzen.lib.common.exec.data.shape.ShapeProvenance
import tech.kzen.lib.common.exec.data.shape.ShapeStability
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.util.digest.Digest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class FilenameDetectionTest {
    private val archiveListing = ArchiveListingFormat("Archive listing", listOf("tar", "tgz"), true)
    private val formats = listOf(archiveListing, ConfiguredDelimitedTestFormats.csv())
    private val dynamicShape = DataShape(
        DataContract(DataType.Dynamic()), ShapeProvenance.Declared, ShapeStability.Stable)


    @Test
    fun tarFamilyNamesFixTheArchiveListingShape() {
        assertEquals(ArchiveListingReaderCapability.shape, FilenameDetection.declaredShape("data.tar.gz", formats))
        assertEquals(ArchiveListingReaderCapability.shape, FilenameDetection.declaredShape("data.TAR.GZ", formats))
        assertEquals(ArchiveListingReaderCapability.shape, FilenameDetection.declaredShape("data.tgz", formats))
        assertEquals(ArchiveListingReaderCapability.shape, FilenameDetection.declaredShape("data.tar", formats))
    }


    @Test
    fun contentDecidesWhenTheEligibleFormatDeclaresNoShape() {
        assertNull(FilenameDetection.declaredShape("orders.csv", formats))
        assertNull(FilenameDetection.declaredShape("orders.csv.gz", formats))
    }


    @Test
    fun unhintedNamesAreDecidedByContent() {
        assertNull(FilenameDetection.declaredShape("data.bin", formats))
        assertNull(FilenameDetection.declaredShape("data.gz", formats))
        assertNull(FilenameDetection.declaredShape("data", formats))
    }


    @Test
    fun eligibleFormatsDisagreeingOnShapeLeaveItToContent() {
        val rival = RivalTarFormat(dynamicShape)
        assertNull(FilenameDetection.declaredShape("data.tar.gz", formats + rival))
    }


    @Test
    fun formatsOutsideDetectionDoNotCompete() {
        val excluded = RivalTarFormat(dynamicShape, automaticDetectionCandidate = false)
        assertEquals(
            ArchiveListingReaderCapability.shape,
            FilenameDetection.declaredShape("data.tar.gz", formats + excluded))
    }


    private class RivalTarFormat(
        private val shape: DataShape,
        override val automaticDetectionCandidate: Boolean = true
    ): ConfiguredRecordFormat {
        override val title = "Rival tar"
        override val extensions = listOf("tar")
        override val catalogVisible = true
        override val hintMetadata = listOf(FormatHintMetadata.structured("tar", listOf("tar")))

        @Suppress("OVERRIDE_DEPRECATION")
        override fun resolvedRead(ref: DataRef): ResolvedReadSpec = throw UnsupportedOperationException()

        override fun declaredShape(): DataShape = shape

        override fun digest(sink: Digest.Sink) {
            sink.addUtf8(title)
        }
    }
}
