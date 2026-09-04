package tech.kzen.auto.server.objects.datasource.format

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.format.FormatResolutionRequest
import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatResolutionResult
import tech.kzen.auto.common.data.format.FormatSelectionKind
import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.format.detection.NormalizedFormatHints
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.DelimitedReadConfig
import tech.kzen.auto.common.data.schema.RecordSchema
import tech.kzen.auto.server.data.read.delimited.ConfiguredDelimitedReaderCapability
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.model.location.ObjectLocation
import kotlin.test.Test
import kotlin.test.assertEquals


class ConfiguredRecordFormatResolutionTest {
    @Test
    fun contextualResolutionPreservesFixedCsvTsvGzipAndExplicitUtf8Specs() = runBlocking {
        val cases = listOf(
            ConfiguredDelimitedTestFormats.csv() to "orders.csv",
            ConfiguredDelimitedTestFormats.csv() to "orders.csv.gz",
            ConfiguredDelimitedTestFormats.tsv() to "orders.tsv")
        for ((format, id) in cases) {
            for (explicitEncoding in listOf(null, "UTF-8")) {
                val ref = DataRef(null, id)

                val contextual = format.resolve(request(ref, explicitEncoding)).resolvedRead

                @Suppress("DEPRECATION")
                val fixed = format.resolvedRead(ref)
                assertEquals(fixed, contextual)
                assertEquals(fixed.digest(), contextual.digest())
            }
        }
    }


    @Test
    fun explicitEncodingIsRebuiltByTheOwningFormat() = runBlocking {
        val format = ConfiguredDelimitedTestFormats.csv()

        val result = format.resolve(request(
            DataRef(null, "legacy.csv"),
            explicitEncoding = "windows-1252"))
        val config = ConfiguredDelimitedReaderCapability.decode(result.resolvedRead.config) as DelimitedReadConfig

        assertEquals("windows-1252", config.characters.charset)
        assertEquals("CSV was selected explicitly with windows-1252", result.detail.reason)
    }


    @Test
    fun preflightKeepsAutomaticCandidateProvenanceAndPinsExplicitCoordinate() = runBlocking {
        val ref = DataRef(null, "orders.csv")
        val concreteReference = "formats.yaml#DetectedSemicolon"
        val concrete = ConfiguredDelimitedTestFormats.csv()
        val automatic = object: ConfiguredRecordFormat by concrete {
            override val selectionKind = FormatSelectionKind.Automatic

            override suspend fun resolve(request: FormatResolutionRequest): FormatResolutionResult =
                FormatResolutionResult(
                    concrete.resolve(request).resolvedRead,
                    FormatResolutionDetail(
                        request.ref,
                        concreteReference,
                        "Semicolon",
                        FormatSelectionKind.Automatic,
                        FormatResolutionBasis.Content,
                        "Semicolon-delimited records were detected"))
        }

        val automaticResult = ConfiguredRecordFormatPreflight(
            "formats.yaml#Automatic", automatic).resolve(request(ref))
        val explicitResult = ConfiguredRecordFormatPreflight(
            "formats.yaml#Csv", concrete).resolve(request(ref))

        assertEquals(concreteReference, automaticResult.detail.concreteFormatReference)
        assertEquals("formats.yaml#Csv", explicitResult.detail.concreteFormatReference)
        assertEquals(FormatSelectionKind.Automatic,
            ConfiguredRecordFormatPreflight("formats.yaml#Automatic", automatic).selectionKind)
    }


    @Test
    fun explicitSchemaBearingFormatReportsLockedColumns() = runBlocking {
        val schema = object: RecordSchema {
            override fun contract() = DataContract(DataType.Record(listOf(
                DataField(FieldId("name"), DataType.Scalar(ScalarKind.Text)))))
        }
        val result = ConfiguredDelimitedTestFormats.csv(schema).resolve(
            request(DataRef(null, "orders.csv")))

        assertEquals(true, result.detail.columnsLocked)
    }


    private fun request(ref: DataRef, explicitEncoding: String? = null) = FormatResolutionRequest(
        DirectContext,
        ref,
        null,
        NormalizedFormatHints.of(ref.id.substringAfterLast('.', "")),
        explicitEncoding)


    private object DirectContext: DataContext {
        override fun argument(name: String): Any? = null

        override suspend fun host(instructions: ObjectLocation, arguments: DataBindings): DataBindings =
            throw UnsupportedOperationException("No active run")

        override suspend fun <R> blocking(block: () -> R): R = block()
    }
}
