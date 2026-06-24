package tech.kzen.auto.server.objects.job

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.job.worker.CsvRecordReader
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.exec.logic.LogicExecution
import tech.kzen.lib.common.exec.logic.LogicExecutionFacade
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultCancelled
import tech.kzen.lib.common.exec.logic.model.LogicResultFailed
import tech.kzen.lib.common.exec.logic.model.LogicResultPaused
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceQuery
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.logic.context.MutableLogicControl
import tech.kzen.lib.server.exec.logic.context.MutableLogicResourceScope
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * Drives [JobDocument] / [JobExecution] over the real notation -> graph -> Logic path in-process (no server),
 * mirroring [tech.kzen.auto.server.objects.flow.FlowExecutionTest]. Exercises the core end-to-end: the
 * channel wiring ([JobChannelCreator] hands each Worker a view of the shared
 * [tech.kzen.auto.server.objects.job.channel.JobChannel] it references), concurrent Workers streaming batched
 * records over Channels with close propagation, the CSV reader / expression filter / writer / preview Workers
 * over real files, the full-speed / pause / step / cancel / deadlock semantics of the quiescence barrier, and
 * the browser -> Worker duplex query bridge (the Preview worker serving a slice query).
 *
 * Jobs are nondeterministic, so assertions are on drained results / terminal status / final output — never
 * interleaving order or an exact pause count.
 */
class JobExecutionTest {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(JobExecutionTest::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val sliceDocumentPath = DocumentPath.parse("test/job-report-slice-test.yaml")
    private val formulaDocumentPath = DocumentPath.parse("test/job-report-formula-test.yaml")
    private val filterExpressionDocumentPath = DocumentPath.parse("test/job-filter-expression-test.yaml")
    private val deadlockDocumentPath = DocumentPath.parse("test/job-deadlock-csv-test.yaml")
    private val previewDocumentPath = DocumentPath.parse("test/job-preview-test.yaml")

    // Relative paths the fixtures wire their reader/writer to; the Workers resolve the same relative strings
    // via Path.of against the shared test-JVM working directory, so test and Workers reach the same files.
    private val sliceDir = Path.of("build/job-slice-test")
    private val sliceInput = sliceDir.resolve("input.csv")
    private val sliceOutput = sliceDir.resolve("output.csv")

    private val formulaDir = Path.of("build/job-formula-test")
    private val formulaInput = formulaDir.resolve("input.csv")
    private val formulaOutput = formulaDir.resolve("output.csv")

    private lateinit var context: KzenAutoContext


    //-----------------------------------------------------------------------------------------------------------------
    @Before
    fun setUp() {
        context = KzenAutoContext.forTest()
    }


    @After
    fun tearDown() {
        context.close()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun slicePipelineFiltersCsvToOutputFile() {
        // CSV reader reads a generated CSV in batches, the expression FilterWorker keeps only the flag=="yes"
        // rows (Kotlin boolean expression over the named columns), the writer writes them back out. Reaching
        // Success proves the batched RecordBatch wiring + real file IO under the quiescence barrier; the output
        // proves the filter ran over the streamed batches, order preserved (single linear pipeline, FIFO).
        val expectedKept = writeSliceInput(1_000)

        val execution = newExecution(sliceDocumentPath)
        execution.beforeStart(TupleValue.empty)

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition(sliceDocumentPath))

        assertIs<LogicResultSuccess>(result)

        val lines = Files.readAllLines(sliceOutput)
        assertEquals("id,flag,value", lines.first())
        val dataLines = lines.drop(1)
        assertEquals(expectedKept, dataLines.size)
        assertTrue(dataLines.all { it.split(",")[1] == "yes" })
    }


    @Test
    fun sliceThroughputBenchmark() {
        // Logs the slice's throughput against a single-threaded inline baseline (read + filter + write, no
        // channels) over the same file. Perf is only logged (nondeterministic); the assertion is that the Job
        // and the baseline produce byte-identical output. Scale rows with -DjobSliceRows= for a heavier run.
        val dataRows = System.getProperty("jobSliceRows")?.toIntOrNull() ?: 100_000
        val expectedKept = writeSliceInput(dataRows)

        val execution = newExecution(sliceDocumentPath)
        execution.beforeStart(TupleValue.empty)

        val jobStart = System.nanoTime()
        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition(sliceDocumentPath))
        val jobNanos = System.nanoTime() - jobStart
        assertIs<LogicResultSuccess>(result)
        val jobOutput = Files.readAllLines(sliceOutput)

        val baselineOutput = sliceDir.resolve("baseline.csv")
        val baselineStart = System.nanoTime()
        Files.newBufferedReader(sliceInput).use { reader ->
            Files.newBufferedWriter(baselineOutput).use { writer ->
                writer.write(reader.readLine())
                writer.newLine()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.split(",")[1] == "yes") {
                        writer.write(line)
                        writer.newLine()
                    }
                }
            }
        }
        val baselineNanos = System.nanoTime() - baselineStart

        assertEquals(expectedKept + 1, jobOutput.size)  // + header line
        assertEquals(Files.readAllLines(baselineOutput), jobOutput)

        logger.info(
            "Slice throughput over {} rows: Job {} rows/s, single-thread baseline {} rows/s",
            dataRows, rowsPerSecond(dataRows, jobNanos), rowsPerSecond(dataRows, baselineNanos))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun expressionFilterKeepsNumericRows() {
        // The FilterWorker's `where` is an arbitrary Kotlin boolean expression over the typed record — here a
        // numeric comparison `amount.number > 2` (the ColumnValue.truthy coercion turns the Boolean result
        // into a keep/drop predicate). Proves the genuine CalculatedColumnEval engine compiles + runs it.
        val dir = Path.of("build/job-filter-expr")
        Files.createDirectories(dir)
        Files.newBufferedWriter(dir.resolve("input.csv")).use {
            it.write("id,amount"); it.newLine()
            for (i in 0..9) {
                it.write("$i,$i"); it.newLine()
            }
        }
        Files.deleteIfExists(dir.resolve("output.csv"))

        val execution = newExecution(filterExpressionDocumentPath)
        execution.beforeStart(TupleValue.empty)

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition(filterExpressionDocumentPath))

        assertIs<LogicResultSuccess>(result)

        val lines = Files.readAllLines(dir.resolve("output.csv"))
        assertEquals("id,amount", lines.first())
        val amounts = lines.drop(1).map { it.split(",")[1].toInt() }
        assertEquals(listOf(3, 4, 5, 6, 7, 8, 9), amounts)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun formulaWorkerAppendsCalculatedColumn() {
        // reader -> FormulaWorker(total = qty * price) -> writer over real files. Reaching Success proves the
        // genuine CalculatedColumnEval engine compiles + runs the Kotlin formula against the batched
        // FlatFileRecords (injected as a @Service); the output proves each row gained a `total` column.
        val dataRows = 200
        Files.createDirectories(formulaDir)
        Files.newBufferedWriter(formulaInput).use { writer ->
            writer.write("id,qty,price")
            writer.newLine()
            for (i in 0 until dataRows) {
                writer.write("$i,${i % 10 + 1},${i % 5 + 1}")
                writer.newLine()
            }
        }

        val execution = newExecution(formulaDocumentPath)
        execution.beforeStart(TupleValue.empty)

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition(formulaDocumentPath))

        assertIs<LogicResultSuccess>(result)

        val lines = Files.readAllLines(formulaOutput)
        assertEquals("id,qty,price,total", lines.first())
        val dataLines = lines.drop(1)
        assertEquals(dataRows, dataLines.size)
        for (line in dataLines) {
            val fields = line.split(",")
            val qty = fields[1].toInt()
            val price = fields[2].toInt()
            assertEquals((qty * price).toDouble(), fields[3].toDouble())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun quotedPathFromCopyAsPathIsTolerated() {
        // Pasting Windows Explorer "Copy as path" yields a path wrapped in double quotes; the reader/writer
        // must strip them (toFilePath) rather than fail Path.of on the leading quote char.
        val documentPath = DocumentPath.parse("test/job-quoted-path-test.yaml")
        val dir = Path.of("build/job-quoted-path")
        Files.createDirectories(dir)
        Files.newBufferedWriter(dir.resolve("input.csv")).use {
            it.write("a,b"); it.newLine()
            it.write("1,2"); it.newLine()
        }
        Files.deleteIfExists(dir.resolve("output.csv"))

        val execution = newExecution(documentPath)
        execution.beforeStart(TupleValue.empty)

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition(documentPath))

        assertIs<LogicResultSuccess>(result)
        assertEquals(listOf("a,b", "1,2"), Files.readAllLines(dir.resolve("output.csv")))
    }


    @Test
    fun quotedFieldsRoundTripThroughReadAndWrite() {
        // RFC-4180 round-trip: an input with quoted fields (embedded comma / quote / newline) read by the
        // reader and re-emitted by the writer must re-parse to the SAME records (the writer's delimiter-aware
        // quoting + the reader's parse are inverses). Asserted at the record level (line-ending agnostic).
        val documentPath = DocumentPath.parse("test/job-quoted-roundtrip-test.yaml")
        val dir = Path.of("build/job-quoted-roundtrip")
        Files.createDirectories(dir)
        Files.newBufferedWriter(dir.resolve("input.csv")).use {
            it.write("id,note"); it.newLine()
            it.write("1,\"hello, world\""); it.newLine()
            it.write("2,\"she said \"\"hi\"\"\""); it.newLine()
            it.write("3,\"line1\nline2\""); it.newLine()
        }
        Files.deleteIfExists(dir.resolve("output.csv"))

        val execution = newExecution(documentPath)
        execution.beforeStart(TupleValue.empty)

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition(documentPath))

        assertIs<LogicResultSuccess>(result)

        val records = mutableListOf<List<String>>()
        CsvRecordReader(Files.newBufferedReader(dir.resolve("output.csv")), ",").use { reader ->
            while (true) {
                val record = reader.readRecord() ?: break
                records.add(record.toList())
            }
        }
        assertEquals(
            listOf(
                listOf("id", "note"),
                listOf("1", "hello, world"),
                listOf("2", "she said \"hi\""),
                listOf("3", "line1\nline2")),
            records)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun headerlessReaderFiltersEveryRow() {
        // A headerless file (`City;Temp`, no header): with header=false the reader treats line 1 as a record
        // and synthesizes positional column names c0, c1, so the expression filter `c0 eq "Lviv"` can run by
        // name; the writer emits no header line. EVERY output row matches the filter, including the first.
        val documentPath = DocumentPath.parse("test/job-headerless-test.yaml")
        val dir = Path.of("build/job-headerless")
        Files.createDirectories(dir)
        Files.newBufferedWriter(dir.resolve("input.csv")).use {
            it.write("Belgrade;-12.3"); it.newLine()   // line 1 is data, NOT a header
            it.write("Lviv;5.1"); it.newLine()
            it.write("Belgrade;0.0"); it.newLine()
            it.write("Lviv;7.7"); it.newLine()
        }
        Files.deleteIfExists(dir.resolve("output.csv"))

        val execution = newExecution(documentPath)
        execution.beforeStart(TupleValue.empty)

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition(documentPath))

        assertIs<LogicResultSuccess>(result)
        assertEquals(listOf("Lviv;5.1", "Lviv;7.7"), Files.readAllLines(dir.resolve("output.csv")))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun pauseThenResumeCompletesOverSlice() {
        // A pre-armed pause lands the slice pipeline mid-run; resuming must complete it with the full,
        // correct output (no records lost or duplicated across the pause barrier). The pipeline is
        // deterministic, so the resumed output equals the un-paused output.
        val expectedKept = writeSliceInput(2_000)

        val execution = newExecution(sliceDocumentPath)
        execution.beforeStart(TupleValue.empty)

        val control = MutableLogicControl(false)
        val resourceScope = MutableLogicResourceScope()
        val graphDefinition = graphDefinition(sliceDocumentPath)

        control.commandPause()
        assertEquals(LogicResultPaused,
            execution.continueOrStart(control, resourceScope, graphDefinition))

        control.commandUnpause()
        val result = execution.continueOrStart(control, resourceScope, graphDefinition)

        assertIs<LogicResultSuccess>(result)
        assertEquals(expectedKept, Files.readAllLines(sliceOutput).drop(1).size)
    }


    @Test
    fun stepEventuallyCompletesOverSlice() {
        // Each step releases the Workers for one global tick (release -> next quiescent wavefront -> re-pause).
        // The step count is nondeterministic, so assert only that repeated stepping converges to Success with
        // the correct output.
        val expectedKept = writeSliceInput(500)

        val execution = newExecution(sliceDocumentPath)
        execution.beforeStart(TupleValue.empty)

        val control = MutableLogicControl(false)
        val resourceScope = MutableLogicResourceScope()
        val graphDefinition = graphDefinition(sliceDocumentPath)

        control.commandPause()

        var result: LogicResult
        var guard = 0
        do {
            control.grantStepBudget(1)
            result = execution.continueOrStart(control, resourceScope, graphDefinition)
            guard += 1
        } while (result is LogicResultPaused && guard < 10_000)

        assertIs<LogicResultSuccess>(result)
        assertEquals(expectedKept, Files.readAllLines(sliceOutput).drop(1).size)
    }


    @Test
    fun cancelTerminatesRunningSlice() {
        // Pause to a known mid-run parked state (deterministic with the large source), then cancel: the parked
        // Worker coroutines must unwind and the run report Cancelled.
        writeSliceInput(5_000)

        val execution = newExecution(sliceDocumentPath)
        execution.beforeStart(TupleValue.empty)

        val control = MutableLogicControl(false)
        val resourceScope = MutableLogicResourceScope()
        val graphDefinition = graphDefinition(sliceDocumentPath)

        control.commandPause()
        assertEquals(LogicResultPaused,
            execution.continueOrStart(control, resourceScope, graphDefinition))

        control.commandCancel()
        assertEquals(LogicResultCancelled,
            execution.continueOrStart(control, resourceScope, graphDefinition))
    }


    @Test
    fun deadlockIsDetected() {
        // A lone writer reading a channel no Worker ever feeds: the run settles quiescent while neither pausing
        // nor complete, which the barrier reports as a failure rather than blocking forever. The output dir is
        // created so the writer opens successfully and then blocks (exercising deadlock detection, not an open
        // failure).
        Files.createDirectories(Path.of("build/job-deadlock-csv"))

        val execution = newExecution(deadlockDocumentPath)
        execution.beforeStart(TupleValue.empty)

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition(deadlockDocumentPath))

        assertIs<LogicResultFailed>(result)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun previewPublishesSampleToTrace() {
        // reader -> PreviewWorker: the preview sink buffers the incoming rows and publishes a teaser (header +
        // first rows + total count) to its `progress` trace child path. Run to completion, then read the final
        // teaser back from the trace store.
        val dir = Path.of("build/job-preview")
        Files.createDirectories(dir)
        Files.newBufferedWriter(dir.resolve("input.csv")).use {
            it.write("id,name"); it.newLine()
            it.write("0,a"); it.newLine()
            it.write("1,b"); it.newLine()
            it.write("2,c"); it.newLine()
        }

        val runExecutionId = LogicRunExecutionId.random()
        val mainLocation = ObjectLocation(previewDocumentPath, ObjectPath.parse("main"))
        val previewLocation = ObjectLocation(previewDocumentPath, ObjectPath.parse("main.workers/preview"))

        val execution = AutoTestUtils.liveLogicExecution(
            context, mainLocation, UnusedLogicHandle, runExecutionId)
        execution.beforeStart(TupleValue.empty)

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition(previewDocumentPath))

        assertIs<LogicResultSuccess>(result)

        val snapshot = context.logicTraceStore.lookup(runExecutionId, LogicTraceQuery(LogicTracePath.root))
        checkNotNull(snapshot)
        val progressPath = JobConventions.workerProgressPath(
            context.objectStableMapper.objectStableId(previewLocation))

        @Suppress("UNCHECKED_CAST")
        val progress = snapshot.values[progressPath]?.value?.get() as? Map<String, Any?>
        assertNotNull(progress, "preview should publish a progress teaser")

        assertEquals(listOf("id", "name"), progress["header"])
        assertEquals(3, (progress["rows"] as List<*>).size)
        assertEquals(3L, progress["count"])
    }


    @Test
    fun previewServesDuplexSliceQuery() {
        // The Preview worker serves an `external` duplex channel: the UI bridge (here driven via the control's
        // request subscriber) sends a slice query and gets a structured reply. Pausing keeps the Worker parked
        // but alive — its serve loop (not gated by checkpoint) still answers — so the bridge round-trip is
        // exercised deterministically. Proves the browser -> Worker request/reply path through a real Worker.
        val dir = Path.of("build/job-preview-query")
        Files.createDirectories(dir)
        Files.newBufferedWriter(dir.resolve("input.csv")).use {
            it.write("id,name"); it.newLine()
            for (i in 0 until 1_000) {
                it.write("$i,n$i"); it.newLine()
            }
        }

        // The fixture's reader path is build/job-preview/input.csv; reuse it for both tests.
        Files.createDirectories(Path.of("build/job-preview"))
        Files.copy(
            dir.resolve("input.csv"),
            Path.of("build/job-preview/input.csv"),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING)

        val execution = newExecution(previewDocumentPath)
        execution.beforeStart(TupleValue.empty)

        val control = MutableLogicControl(false)
        val resourceScope = MutableLogicResourceScope()
        val graphDefinition = graphDefinition(previewDocumentPath)

        control.commandPause()
        assertEquals(LogicResultPaused,
            execution.continueOrStart(control, resourceScope, graphDefinition))

        try {
            val reply = queryPreviewSlice(control, "queries")
            assertNotNull(reply, "duplex slice query should reply")
            assertTrue(reply.containsKey("rows"), "reply should carry a rows slice")
            assertTrue(reply.containsKey("count"), "reply should carry the total count")
        }
        finally {
            control.commandCancel()
            assertEquals(LogicResultCancelled,
                execution.continueOrStart(control, resourceScope, graphDefinition))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun failedWorkerSurfacesAsFailureOnItsTrace() {
        // A reader pointed at a missing file fails immediately. The failure (with reason) must reach the
        // Worker's trace — the Job panel's run feedback.
        val documentPath = DocumentPath.parse("test/job-missing-input-test.yaml")
        val dir = Path.of("build/job-missing-input")
        if (Files.exists(dir)) {
            dir.toFile().deleteRecursively()
        }

        val runExecutionId = LogicRunExecutionId.random()
        val mainLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
        val readerLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/reader"))

        val execution = AutoTestUtils.liveLogicExecution(
            context, mainLocation, UnusedLogicHandle, runExecutionId)
        execution.beforeStart(TupleValue.empty)

        val result = execution.continueOrStart(
            MutableLogicControl(false), MutableLogicResourceScope(), graphDefinition(documentPath))

        assertIs<LogicResultFailed>(result)

        val snapshot = context.logicTraceStore.lookup(
            runExecutionId, LogicTraceQuery(LogicTracePath.root))
        checkNotNull(snapshot)
        val readerTrace = snapshot.values[
            LogicTracePath.ofObjectStableId(context.objectStableMapper.objectStableId(readerLocation))
        ]?.value?.get()?.toString()
        assertTrue(
            readerTrace != null && readerTrace.startsWith("failed:"),
            "reader trace should report the failure, was: $readerTrace")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Writes a generated CSV (header `id,flag,value`, then `dataRows` rows with `flag` alternating yes/no) to
    // the slice fixture's input path, returning the number of flag=="yes" rows the filter should keep.
    private fun writeSliceInput(dataRows: Int): Int {
        Files.createDirectories(sliceDir)
        var kept = 0
        Files.newBufferedWriter(sliceInput).use { writer ->
            writer.write("id,flag,value")
            writer.newLine()
            for (i in 0 until dataRows) {
                val flag = if (i % 2 == 0) "yes" else "no"
                if (flag == "yes") {
                    kept += 1
                }
                writer.write("$i,$flag,v$i")
                writer.newLine()
            }
        }
        return kept
    }


    private fun rowsPerSecond(rows: Int, nanos: Long): Long {
        if (nanos <= 0L) {
            return 0L
        }
        return rows * 1_000_000_000L / nanos
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Sends a slice query to the Preview worker's external duplex channel, polling past the brief async window
    // before the run's request subscriber / serving Worker is up. Returns the reply map, or null.
    private fun queryPreviewSlice(control: MutableLogicControl, channelName: String): Map<*, *>? {
        val request = ExecutionRequest(
            RequestParams.of(
                JobConventions.channelParameter to channelName,
                JobConventions.previewOffsetParameter to "0",
                JobConventions.previewLimitParameter to "5"),
            null)

        repeat(50) {
            val result = control.publishRequest(request)
            if (result is ExecutionSuccess) {
                return result.value.get() as? Map<*, *>
            }
            Thread.sleep(20)
        }
        return null
    }


    private fun graphDefinition(documentPath: DocumentPath): GraphDefinition {
        return AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation())
            .transitiveSuccessful
            .filterTransitive(documentPath)
    }


    private fun newExecution(documentPath: DocumentPath): LogicExecution {
        val mainLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
        return AutoTestUtils.liveLogicExecution(context, mainLocation, UnusedLogicHandle)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A Job's Workers communicate only over channels and never start a nested logic, so the handle is unused.
    private object UnusedLogicHandle: LogicHandle {
        override fun start(
            logicRunExecutionId: LogicRunExecutionId,
            originalObjectLocation: ObjectLocation
        ): LogicExecutionFacade =
            error("nested logic should not start for a Job")
    }
}
