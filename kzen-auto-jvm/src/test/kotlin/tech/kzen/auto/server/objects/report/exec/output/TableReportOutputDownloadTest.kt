package tech.kzen.auto.server.objects.report.exec.output

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.objects.document.report.listing.AnalysisColumnInfo
import tech.kzen.auto.common.objects.document.report.listing.FilteredHeaderListing
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.objects.document.report.spec.PreviewSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisFlatDataSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.AnalysisType
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueColumnSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueTableSpec
import tech.kzen.auto.common.objects.document.report.spec.analysis.pivot.PivotValueType
import tech.kzen.auto.common.objects.document.report.spec.filter.FilterSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExploreSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExportSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputSpec
import tech.kzen.auto.common.objects.document.report.spec.output.OutputType
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.input.model.data.DatasetInfo
import tech.kzen.auto.server.objects.report.exec.output.flat.IndexedCsvTable
import tech.kzen.auto.server.objects.report.exec.output.pivot.PivotBuilder
import tech.kzen.auto.server.objects.report.model.ReportRunContext
import tech.kzen.auto.server.paradigm.detached.ExecutionDownloadContent
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.platform.ClassName
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * Both branches of [TableReportOutput.downloadCsvOffline]: a flat run resolves to the table already on disk,
 * a pivot run generates into the sink it is handed. Materializes the output directly rather than running the
 * engine - the engine is covered by ReportNotationTest, and what is under test here is the mapping from
 * analysis type to download content.
 */
class TableReportOutputDownloadTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val header = HeaderListing.of(listOf("city", "amount"))

    private val rows = listOf(
        listOf("Lviv", "10"),
        listOf("Kyiv", "20"),
        listOf("Lviv", "30"),
        listOf("Odesa", "40"))

    private val pivotSpec = PivotSpec(
        HeaderListing.of(listOf("city")),
        PivotValueTableSpec(mapOf(
            header.values[1] to PivotValueColumnSpec(setOf(PivotValueType.Count, PivotValueType.Sum)))))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun flatRunResolvesToTheTableAlreadyOnDisk() {
        withTempDir("table-report-output-flat") { dir ->
            IndexedCsvTable(header, dir).apply {
                rows.forEach { add(FlatFileRecord.of(it), header) }
                close(error = false)
            }

            val content = TableReportOutput.downloadCsvOffline(
                runContext(dir, AnalysisType.FlatData))

            val ofFile = assertIs<ExecutionDownloadContent.OfFile>(content)
            assertEquals(IndexedCsvTable.tablePath(dir), ofFile.path)
            assertTrue(Files.exists(ofFile.path))
            assertEquals(
                "city,amount\r\n" +
                        "Lviv,10\r\n" +
                        "Kyiv,20\r\n" +
                        "Lviv,30\r\n" +
                        "Odesa,40\r\n",
                Files.readString(ofFile.path))
        }
    }


    @Test
    fun pivotRunGeneratesTheExportIntoTheSink() {
        withTempDir("table-report-output-pivot") { dir ->
            materializePivot(dir)

            val content = TableReportOutput.downloadCsvOffline(
                runContext(dir, AnalysisType.PivotTable))

            val ofWriter = assertIs<ExecutionDownloadContent.OfWriter>(content)
            val sink = ByteArrayOutputStream()
            runBlocking {
                ofWriter.write(sink)
            }

            // The export header renders the column name alone, so a column carrying two aggregates yields two
            // identically named cells.
            assertEquals(
                "city,amount,amount\n" +
                        "Lviv,2,40\n" +
                        "Kyiv,1,20\n" +
                        "Odesa,1,40",
                sink.toString(Charsets.UTF_8))
        }
    }


    @Test
    fun pivotSinkFailurePropagatesAndReleasesTheBuilder() {
        withTempDir("table-report-output-pivot-failure") { dir ->
            materializePivot(dir)

            val content = TableReportOutput.downloadCsvOffline(
                runContext(dir, AnalysisType.PivotTable))
            val ofWriter = assertIs<ExecutionDownloadContent.OfWriter>(content)

            val failure = assertFailsWith<IOException> {
                runBlocking {
                    ofWriter.write(object: OutputStream() {
                        override fun write(b: Int) {
                            throw IOException("sink failed")
                        }
                    })
                }
            }
            assertEquals("sink failed", failure.message)

            // PivotBuilder.create opens H2 stores and RandomAccessFile handles eagerly, and Windows refuses to
            // delete a file with an open handle - so a successful delete is the proof that the failing write
            // still closed the builder.
            WorkUtils.recursivelyDeleteDir(dir)
            assertTrue(!Files.exists(dir))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun materializePivot(dir: Path) {
        PivotBuilder
            .create(pivotSpec.rows, HeaderListing(pivotSpec.values.columns.keys.toList()), dir)
            .use { pivotBuilder ->
                rows.forEach { pivotBuilder.add(FlatFileRecord.of(it), header) }
            }
    }


    private fun runContext(runDir: Path, analysisType: AnalysisType): ReportRunContext {
        return ReportRunContext(
            runDir,
            DocumentName("download-test"),
            ClassName(FlatFileRecord::class.java.name),
            DatasetInfo(listOf()),
            AnalysisColumnInfo(
                FilteredHeaderListing.ofAll(header),
                FilteredHeaderListing.ofAll(HeaderListing.empty),
                null,
                null),
            FormulaSpec(mapOf()),
            PreviewSpec(false),
            FilterSpec(mapOf()),
            PreviewSpec(false),
            AnalysisSpec(analysisType, AnalysisFlatDataSpec.empty, pivotSpec),
            OutputSpec(
                OutputType.Explore,
                OutputExploreSpec(1, 50),
                OutputExportSpec("csv", "none", ""),
                runDir.toString()))
    }


    private fun withTempDir(name: String, use: (Path) -> Unit) {
        val dir = Files.createTempDirectory(name)
        try {
            use(dir)
        }
        finally {
            if (Files.exists(dir)) {
                WorkUtils.recursivelyDeleteDir(dir)
            }
        }
    }
}
