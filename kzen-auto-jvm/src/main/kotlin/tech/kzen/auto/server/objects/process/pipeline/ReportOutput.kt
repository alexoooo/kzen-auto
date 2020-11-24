package tech.kzen.auto.server.objects.process.pipeline

import org.apache.commons.csv.CSVFormat
import tech.kzen.auto.common.objects.document.process.OutputInfo
import tech.kzen.auto.common.objects.document.process.OutputPreview
import tech.kzen.auto.common.objects.document.process.OutputSpec
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.server.objects.process.filter.IndexedCsvTable
import tech.kzen.auto.server.objects.process.model.ProcessRunSignature
import tech.kzen.auto.server.objects.process.model.ProcessRunSpec
import tech.kzen.auto.server.objects.process.model.RecordItem
import tech.kzen.auto.server.objects.process.pivot.PivotBuilder
import tech.kzen.auto.server.objects.process.pivot.row.RowIndex
import tech.kzen.auto.server.objects.process.pivot.row.digest.H2DigestIndex
import tech.kzen.auto.server.objects.process.pivot.row.signature.StoreRowSignatureIndex
import tech.kzen.auto.server.objects.process.pivot.row.signature.store.BufferedIndexedSignatureStore
import tech.kzen.auto.server.objects.process.pivot.row.signature.store.FileIndexedSignatureStore
import tech.kzen.auto.server.objects.process.pivot.row.value.StoreRowValueIndex
import tech.kzen.auto.server.objects.process.pivot.row.value.store.BufferedIndexedTextStore
import tech.kzen.auto.server.objects.process.pivot.row.value.store.FileIndexedTextStore
import tech.kzen.auto.server.objects.process.pivot.stats.BufferedValueStatistics
import tech.kzen.auto.server.objects.process.pivot.stats.store.FileValueStatisticsStore
import tech.kzen.auto.server.objects.process.pivot.store.BufferedOffsetStore
import tech.kzen.auto.server.objects.process.pivot.store.FileOffsetStore
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


class ReportOutput(
    initialProcessRunSpec: ProcessRunSpec,
    private val runDir: Path,
    private val handle: TaskHandle?
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val modifiedFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")


        private fun headerNames(processRunSpec: ProcessRunSpec): List<String> {
            val runSignature = processRunSpec.toSignature()

            return when {
                runSignature.hasPivot() ->
                    PivotBuilder.ExportSignature.of(
                        processRunSpec.pivot.rows.toList(),
                        processRunSpec.pivot.values
                    ).header

                else ->
                    runSignature.columnNames
            }
        }


        private fun missingOutputInfo(processRunSpec: ProcessRunSpec, runDir: Path): OutputInfo {
            val header = headerNames(processRunSpec)

            return OutputInfo(
                "Missing, will be created: $runDir",
                null,
                0L,
                OutputPreview(header, listOf(), 0L)
            )
        }


        private fun filePivot(
            rows: Set<String>,
            values: Set<String>,
            tempDir: Path
        ): PivotBuilder {
            val rowTextContentFile = tempDir.resolve("row-text-value.bin")
            val rowTextIndexFile= tempDir.resolve("row-text-index.bin")
            val rowSignatureFile = tempDir.resolve("row-signature.bin")
            val valueStatisticsFile = tempDir.resolve("value-statistics.bin")
            val rowValueDigestDir = tempDir.resolve("row-text-digest")
            val rowSignatureDigestDir = tempDir.resolve("row-signature-digest")

            val rowValueDigestIndex =
                H2DigestIndex(rowValueDigestDir)

            val textOffsetStore = BufferedOffsetStore(
                FileOffsetStore(rowTextIndexFile)
            )

            val indexedTextStore = BufferedIndexedTextStore(
                FileIndexedTextStore(rowTextContentFile, textOffsetStore)
            )

            val rowValueIndex = StoreRowValueIndex(
                rowValueDigestIndex, indexedTextStore)

            val rowSignatureDigestIndex =
                H2DigestIndex(rowSignatureDigestDir)

            val indexedSignatureStore = BufferedIndexedSignatureStore(
                FileIndexedSignatureStore(rowSignatureFile, rows.size)
            )

            val rowSignatureIndex = StoreRowSignatureIndex(
                rowSignatureDigestIndex, indexedSignatureStore)

            val valueStatistics = BufferedValueStatistics(
                FileValueStatisticsStore(valueStatisticsFile, values.size)
            )

            return PivotBuilder(
                rows,
                values,
                RowIndex(rowValueIndex, rowSignatureIndex),
                valueStatistics)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private data class SaveInfo(
        val path: Path,
        val defaultPath: Path,
        val custom: Boolean,
        val customInvalid: Boolean,
        val message: String
    )


    private data class PreviewRequest(
        val processRunSpec: ProcessRunSpec,
        val outputSpec: OutputSpec
    )


    //-----------------------------------------------------------------------------------------------------------------
    private val processRunSignature: ProcessRunSignature

    private val indexedCsvTable: IndexedCsvTable?
    private val pivotBuilder: PivotBuilder?


    @Volatile
    private var previewRequest: PreviewRequest? = null

    @Volatile
    private var previewResponse = CompletableFuture<OutputInfo>()


    //-----------------------------------------------------------------------------------------------------------------
    init {
        processRunSignature = initialProcessRunSpec.toSignature()

        if (! Files.exists(runDir)) {
            pivotBuilder = null
            indexedCsvTable = null
        }
        else {
            if (! processRunSignature.hasPivot()) {
                indexedCsvTable = IndexedCsvTable(processRunSignature.columnNames, runDir)
                pivotBuilder = null
            }
            else {
                indexedCsvTable = null

                val pivotSpec = initialProcessRunSpec.pivot
                pivotBuilder = filePivot(pivotSpec.rows, pivotSpec.values.columns.keys, runDir)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun add(row: RecordItem) {
        handlePreviewRequest()

        if (indexedCsvTable != null) {
            val values = row.getOrEmptyAll(processRunSignature.columnNames)
            indexedCsvTable.add(values)
        }
        else {
            pivotBuilder!!.add(row)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Synchronized
    fun preview(processRunSpec: ProcessRunSpec, outputSpec: OutputSpec): OutputInfo {
        if (! Files.exists(runDir)) {
            return missingOutputInfo(processRunSpec, runDir)
        }

        return if (handle == null) {
            previewInCurrentThread(processRunSpec, outputSpec)
        }
        else {
            val requestHandle = PreviewRequest(processRunSpec, outputSpec)

            previewRequest = requestHandle

            var response: OutputInfo? = null
            while (response == null) {
                response =
                    try {
                        previewResponse.get(1, TimeUnit.SECONDS)
                    }
                    catch (e: TimeoutException) {
                        continue
                    }
            }

            previewRequest = null
            previewResponse = CompletableFuture()

            response
        }
    }


    private fun previewInCurrentThread(processRunSpec: ProcessRunSpec, outputSpec: OutputSpec): OutputInfo {
        val zeroBasedPreview = outputSpec.previewStartZeroBased()

        val fileTime = Files.getLastModifiedTime(runDir)
        val formattedTime = formatTime(fileTime.toInstant())

        val rowCount: Long
        val preview =
            if (indexedCsvTable != null) {
                rowCount = indexedCsvTable.rowCount()
                indexedCsvTable.preview(
                    zeroBasedPreview, outputSpec.previewCount)
            }
            else {
                check(pivotBuilder != null)
                rowCount = pivotBuilder.rowCount()
                pivotBuilder.preview(
                    processRunSpec.pivot.values, zeroBasedPreview, outputSpec.previewCount)
            }

        val saveInfo = saveInfo(runDir, outputSpec)

        return OutputInfo(
            saveInfo.message,
            formattedTime,
            rowCount,
            preview)
    }


    fun save(processRunSpec: ProcessRunSpec, outputSpec: OutputSpec): Path? {
        check(handle == null)

        if (! Files.exists(runDir)) {
            return null
        }

        val runSignature = processRunSpec.toSignature()

        val saveInfo = saveInfo(runDir, outputSpec)
        val outputPath = saveInfo.path

        if (! runSignature.hasPivot() &&
            saveInfo.path.toAbsolutePath().normalize() ==
            saveInfo.defaultPath.toAbsolutePath().normalize())
        {
            // NB: don't override source of truth
            return outputPath
        }

        Files.newBufferedWriter(outputPath).use { output ->
            val csvPrinter = CSVFormat.DEFAULT.print(output)

            if (indexedCsvTable != null) {
                indexedCsvTable.traverseWithHeader { row ->
                    csvPrinter.printRecord(row)
                }
            }
            else {
                check(pivotBuilder != null)
                pivotBuilder.traverseWithHeader(processRunSpec.pivot.values) { row ->
                    csvPrinter.printRecord(row)
                }
            }
        }

        return outputPath
    }


    private fun formatTime(instant: Instant): String {
        return instant
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(modifiedFormatter)
    }


    private fun saveInfo(runDir: Path, outputSpec: OutputSpec): SaveInfo {
        val defaultPath = runDir.resolve(IndexedCsvTable.tableFile)

        val customPath: Path?
        val customInvalid: Boolean
        if (outputSpec.savePath.isBlank()) {
            customPath = null
            customInvalid = false
        }
        else {
            customPath = try {
                Paths.get(outputSpec.savePath)
            } catch (e: InvalidPathException) {
                null
            }
            customInvalid = customPath == null
        }

        val pathWithFallback = customPath ?: defaultPath

        val existsMessage =
            if (Files.exists(pathWithFallback)) {
                "already exists"
            }
            else {
                "does not exist (will create)"
            }

        val typeMessage = when {
            customInvalid ->
                "Invalid path provided (using default)"

            customPath == null ->
                "Using default path"

            else ->
                "Using custom path"
        }

        val message = "$typeMessage, $existsMessage: ${pathWithFallback.toAbsolutePath().normalize()}"

        return SaveInfo(
            pathWithFallback,
            defaultPath,
            customPath != null || customInvalid,
            customInvalid,
            message
        )
    }


    private fun handlePreviewRequest() {
        val request = previewRequest
            ?: return

        val outputInfo = previewInCurrentThread(request.processRunSpec, request.outputSpec)

        previewResponse.complete(outputInfo)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        indexedCsvTable?.close()
        pivotBuilder?.close()
    }
}