package tech.kzen.auto.server.exec.job.bench

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.report.output.OutputInfo
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.plugin.model.record.FlatRecordHeader
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.job.JobLogicCompiler
import tech.kzen.auto.server.exec.report.ReportLogicCompiler
import tech.kzen.auto.server.exec.report.ReportRun
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.value.JobValueClaim
import tech.kzen.auto.server.objects.job.value.RecordOutputBuilder
import tech.kzen.auto.server.objects.report.ReportDocument
import tech.kzen.auto.server.objects.report.model.ReportRunContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.DefaultDataAdapterRegistry
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.auto.server.exec.emptyBindings
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.server.exec.engine.RunEngine
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max
import kotlin.test.assertEquals
import kotlin.test.assertTrue


object JobReportBenchmark {
    private const val defaultRows = 1_000_000
    private const val defaultRuns = 3
    private const val warmupRuns = 1
    private const val stationCount = 400
    private const val nanosPerSecond = 1_000_000_000.0
    private const val nanosPerMillisecond = 1_000_000.0
    private const val reportPreviewStart = 0
    private const val numericTolerance = 0.000_000_001
    private const val sequentialCalculatedColumns = 8

    private val sliceJob = DocumentPath.parse("test/bench-job-slice.yaml")
    private val aggregateJob = DocumentPath.parse("test/bench-job-aggregate.yaml")
    private val headerlessAggregateJob = DocumentPath.parse("test/bench-job-aggregate-headerless.yaml")
    private val exportJob = DocumentPath.parse("test/bench-job-export.yaml")
    private val aggregateReport = DocumentPath.parse("test/bench-report-aggregate.yaml")
    private val exportReport = DocumentPath.parse("test/bench-report-export.yaml")

    private val valueField = FieldId("value")
    private val calculatedFields = List(sequentialCalculatedColumns) { FieldId("calculated$it") }
    private val flatValueHeader = FlatRecordHeader(DataContract(DataType.Record(listOf(
        DataField(FieldId("id"), DataType.Scalar(ScalarKind.Text)),
        DataField(valueField, DataType.Scalar(ScalarKind.Floating(64)))))))

    private val sliceInput = Path.of("build/bench/slice/input.csv")
    private val sliceJobOutput = Path.of("build/bench/slice/job-output.csv")
    private val sliceInlineOutput = Path.of("build/bench/slice/inline-output.csv")
    private val aggregateInput = Path.of("build/bench/agg/input.csv")
    private val headerlessAggregateInput = Path.of("build/bench/agg/input-headerless.csv")
    private val aggregateJobOutput = Path.of("build/bench/agg/job-output.csv")
    private val headerlessAggregateJobOutput = Path.of("build/bench/agg/job-headerless-output.csv")
    private val aggregateInlineOutput = Path.of("build/bench/agg/inline-output.csv")
    private val exportInput = Path.of("build/bench/exp/input.csv")
    private val jobExportOutput = Path.of("build/bench/exp/job-export.csv")
    private val reportExportOutput = Path.of("build/bench/exp/report-export.csv")

    @Volatile
    private var blackhole = 0


    @JvmStatic
    fun main(args: Array<String>) {
        val rows = System.getProperty("benchRows")?.toIntOrNull() ?: defaultRows
        val runs = System.getProperty("benchRuns")?.toIntOrNull() ?: defaultRuns
        require(rows > 0) { "benchRows must be positive" }
        require(runs > 0) { "benchRuns must be positive" }

        val selected = System.getProperty("benchScenarios")
            ?.split(',')
            ?.map { it.trim().uppercase() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: Scenario.entries.map { it.name }.toSet()
        val unknown = selected - Scenario.entries.map { it.name }.toSet()
        require(unknown.isEmpty()) { "Unknown benchmark scenarios: $unknown" }

        val results = buildList {
            if (Scenario.S0.name in selected) addAll(benchmarkCarrier(rows, runs))
            if (Scenario.S1.name in selected) addAll(benchmarkSlice(rows, runs))
            if (Scenario.S2.name in selected) addAll(benchmarkAggregate(rows, runs))
            if (Scenario.S2H.name in selected) addAll(benchmarkHeaderlessAggregate(rows, runs))
            if (Scenario.S3.name in selected) addAll(benchmarkExport(rows, runs))
        }
        printResults(results)
    }


    fun verifySlice(rows: Int) {
        BenchmarkData.writeFlagged(rows, sliceInput)
        runJobOnce(sliceJob)
        runInlineSlice()
        assertEquals(-1L, Files.mismatch(sliceJobOutput, sliceInlineOutput))
    }

    fun verifyAggregate(rows: Int) {
        BenchmarkData.writeStations(rows, aggregateInput, header = true)
        runJobOnce(aggregateJob)
        val reportInfo = runReportOnce(aggregateReport)
        val expected = runInlineAggregate(aggregateInput, header = true, aggregateInlineOutput)

        assertEquals(stationCount + 1L, Files.lines(aggregateJobOutput).use { it.count() })
        assertEquals(stationCount.toLong(), reportInfo.table?.rowCount)
        assertPivotRow(expected.getValue("s000"), readCsvPivotRow(aggregateJobOutput, "s000"))

        val reportPreview = requireNotNull(reportInfo.table?.preview)
        val reportIndex = reportPreview.rows.indexOfFirst { it.firstOrNull() == "s000" }
        assertTrue(reportIndex >= 0, "Report preview missing s000")
        assertPivotRow(expected.getValue("s000"), reportPreview.rows[reportIndex].drop(1))
    }

    fun verifyHeaderlessAggregate(rows: Int) {
        BenchmarkData.writeStations(rows, headerlessAggregateInput, header = false)
        runJobOnce(headerlessAggregateJob)
        assertEquals(stationCount + 1L, Files.lines(headerlessAggregateJobOutput).use { it.count() })
    }

    fun verifyExport(rows: Int) {
        BenchmarkData.writeWide(rows, exportInput)
        runJobOnce(exportJob)
        runReportOnce(exportReport)
        assertEquals(-1L, Files.mismatch(jobExportOutput, reportExportOutput))
    }


    private fun benchmarkCarrier(rows: Int, runs: Int): List<BenchmarkResult> {
        val header = HeaderListing.ofUnique(listOf("id", "value"))
        return listOf(
            benchmark(Scenario.S0, "flat-record", rows, runs) {
                lambdaExecution { carrierRecordLoop(rows) }
            },
            benchmark(Scenario.S0, "data-value+direct-flat", rows, runs) {
                lambdaExecution { dataValueCarrierLoop(rows, header) }
            },
            benchmark(Scenario.S0, "flat-value-direct", rows, runs) {
                lambdaExecution { directFlatValueLoop(rows) }
            },
            benchmark(Scenario.S0, "flat-value+8-appends", rows, runs) {
                lambdaExecution { flatBuilderLoop(rows) }
            },
            benchmark(Scenario.S0, "native-value+8-appends", rows, runs) {
                lambdaExecution { nativeBuilderLoop(rows) }
            })
    }

    private fun benchmarkSlice(rows: Int, runs: Int): List<BenchmarkResult> {
        BenchmarkData.writeFlagged(rows, sliceInput)
        val results = listOf(
            benchmark(Scenario.S1, "job", rows, runs) { prepareJob(sliceJob) },
            benchmark(Scenario.S1, "inline", rows, runs) { lambdaExecution(::runInlineSlice) })
        assertEquals(-1L, Files.mismatch(sliceJobOutput, sliceInlineOutput))
        return results
    }

    private fun benchmarkAggregate(rows: Int, runs: Int): List<BenchmarkResult> {
        BenchmarkData.writeStations(rows, aggregateInput, header = true)
        val results = listOf(
            benchmark(Scenario.S2, "job", rows, runs) { prepareJob(aggregateJob) },
            benchmark(Scenario.S2, "report", rows, runs) { prepareReport(aggregateReport) },
            benchmark(Scenario.S2, "inline", rows, runs) {
                lambdaExecution { runInlineAggregate(aggregateInput, header = true, aggregateInlineOutput) }
            })
        verifyAggregate(rows)
        return results
    }

    private fun benchmarkHeaderlessAggregate(rows: Int, runs: Int): List<BenchmarkResult> {
        BenchmarkData.writeStations(rows, headerlessAggregateInput, header = false)
        val result = benchmark(Scenario.S2H, "job", rows, runs) { prepareJob(headerlessAggregateJob) }
        assertEquals(stationCount + 1L, Files.lines(headerlessAggregateJobOutput).use { it.count() })
        return listOf(result)
    }

    private fun benchmarkExport(rows: Int, runs: Int): List<BenchmarkResult> {
        BenchmarkData.writeWide(rows, exportInput)
        val results = listOf(
            benchmark(Scenario.S3, "job", rows, runs) { prepareJob(exportJob) },
            benchmark(Scenario.S3, "report", rows, runs) { prepareReport(exportReport) })
        assertEquals(-1L, Files.mismatch(jobExportOutput, reportExportOutput))
        return results
    }


    private fun benchmark(
        scenario: Scenario,
        implementation: String,
        units: Int,
        runs: Int,
        prepare: () -> PreparedExecution
    ): BenchmarkResult {
        repeat(warmupRuns) {
            execute(prepare(), units)
        }
        val measurements = List(runs) {
            execute(prepare(), units)
        }.sortedBy { it.wallNanos }
        return BenchmarkResult(scenario, implementation, units, measurements[measurements.size / 2], measurements)
    }

    private fun execute(prepared: PreparedExecution, units: Int): Measurement {
        prepared.use {
            val gcBefore = gcSnapshot()
            val heapBefore = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
            val start = System.nanoTime()
            val outcome = it.execute()
            val wallNanos = System.nanoTime() - start
            val heapAfter = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
            val gcAfter = gcSnapshot()
            check(outcome is Outcome.Success) { "Benchmark execution failed: $outcome" }
            return Measurement(
                wallNanos,
                gcAfter.collections - gcBefore.collections,
                gcAfter.millis - gcBefore.millis,
                heapAfter - heapBefore,
                units)
        }
    }


    private fun prepareJob(documentPath: DocumentPath): PreparedExecution {
        val context = KzenAutoContext.forTest()
        try {
            val graphNotation = AutoTestUtils.readNotation()
            val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful
            val location = ObjectLocation(documentPath, ObjectPath.parse("main"))
            val logic = JobLogicCompiler.compile(
                location, graphNotation, graphDefinition, compilerServices(context))
            return EngineExecution(
                RunEngine(logic, context.objectStableMapper.objectStableId(location)),
                context)
        }
        catch (e: Throwable) {
            context.close()
            throw e
        }
    }

    private fun prepareReport(documentPath: DocumentPath): PreparedExecution {
        val context = KzenAutoContext.forTest()
        try {
            val graphNotation = AutoTestUtils.readNotation()
            val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful
            val location = ObjectLocation(documentPath, ObjectPath.parse("main"))
            val graphInstance = GraphCreator.createGraph(
                graphDefinition.filterTransitive(documentPath), context.graphEnvironment)
            val reportDocument = graphInstance[location]?.reference as? ReportDocument
                ?: error("Report document not found: $location")
            val runContext = reportDocument.reportRunContext()
                ?: error("Report run context unavailable: $location")
            val logic = ReportLogicCompiler.compile(
                location, graphNotation, graphDefinition, compilerServices(context))
            return ReportExecution(
                RunEngine(logic, context.objectStableMapper.objectStableId(location)),
                context,
                runContext)
        }
        catch (e: Throwable) {
            context.close()
            throw e
        }
    }

    private fun compilerServices(context: KzenAutoContext): LogicCompilerServices =
        LogicCompilerServices(
            context.graphEnvironment,
            context.objectStableMapper,
            context.cachedKotlinCompiler,
            context.scriptValidationCache,
            context.jobValidationCache,
            context.notationMetadataReader,
            context.jobWorkPool,
            LogicRunExecutionId.random())

    private fun runJobOnce(documentPath: DocumentPath) {
        prepareJob(documentPath).use {
            check(it.execute() is Outcome.Success)
        }
    }

    private fun runReportOnce(documentPath: DocumentPath): OutputInfo {
        val prepared = prepareReport(documentPath) as ReportExecution
        prepared.use {
            check(it.execute() is Outcome.Success)
            return it.outputInfo()
        }
    }


    private fun runInlineSlice() {
        Files.newBufferedReader(sliceInput).use { reader ->
            Files.newBufferedWriter(sliceInlineOutput).use { writer ->
                writer.append(requireNotNull(reader.readLine()))
                writer.newLine()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.substringAfter(',').substringBefore(',') == "yes") {
                        writer.append(line)
                        writer.newLine()
                    }
                }
            }
        }
    }

    private fun runInlineAggregate(input: Path, header: Boolean, output: Path): Map<String, StationStats> {
        val statistics = HashMap<String, StationStats>(stationCount)
        Files.newBufferedReader(input).use { reader ->
            if (header) {
                requireNotNull(reader.readLine())
            }
            while (true) {
                val line = reader.readLine() ?: break
                val delimiter = line.indexOf(',')
                val station = line.substring(0, delimiter)
                val value = line.substring(delimiter + 1).toDouble()
                statistics.getOrPut(station, ::StationStats).add(value)
            }
        }

        Files.newBufferedWriter(output).use { writer ->
            writer.append("station,min,average,max\n")
            for ((station, stats) in statistics.toSortedMap()) {
                writer.append("$station,${stats.min},${stats.average()},${stats.max}\n")
            }
        }
        return statistics
    }

    private fun readCsvPivotRow(path: Path, station: String): List<String> {
        Files.newBufferedReader(path).use { reader ->
            requireNotNull(reader.readLine())
            while (true) {
                val line = reader.readLine() ?: break
                val values = line.split(',')
                if (values.first() == station) {
                    return values.drop(1)
                }
            }
        }
        error("Pivot row missing: $station")
    }

    private fun assertPivotRow(expected: StationStats, actual: List<String>) {
        assertEquals(3, actual.size)
        assertEquals(expected.min, actual[0].toDouble(), numericTolerance)
        assertEquals(expected.average(), actual[1].toDouble(), numericTolerance)
        assertEquals(expected.max, actual[2].toDouble(), numericTolerance)
    }


    private fun carrierRecordLoop(rows: Int) {
        var checksum = 0
        repeat(rows) { index ->
            val record = FlatFileRecord.of(index.toString(), "v$index")
            checksum += record.fieldCount() + System.identityHashCode(record)
        }
        blackhole = checksum
    }

    private fun dataValueCarrierLoop(rows: Int, header: HeaderListing) {
        var checksum = 0
        repeat(rows) { index ->
            val record = FlatFileRecord.of(index.toString(), "v$index")
            val value = JobDataValues.flat(header, record)
            checksum += record.fieldCount() + System.identityHashCode(value)
        }
        blackhole = checksum
    }


    private fun directFlatValueLoop(rows: Int) {
        var checksum = 0.0
        repeat(rows) { index ->
            val record = FlatFileRecord.of(index.toString(), (index + 0.5).toString())
            record.attachHeader(flatValueHeader)
            val value = DataValue(record, DataNode(0))
            checksum += value.access.readDouble(value.access.field(value.root, valueField))
        }
        blackhole = checksum.toInt()
    }


    private fun flatBuilderLoop(rows: Int) {
        var checksum = 0L
        repeat(rows) { index ->
            val record = FlatFileRecord.of(index.toString(), (index + 0.5).toString())
            record.attachHeader(flatValueHeader)
            var value = DataValue(record, DataNode(0))
            for (fieldIndex in calculatedFields.indices) {
                val builder = RecordOutputBuilder.open(JobValueClaim(value, exclusive = true))
                builder.append(
                    calculatedFields[fieldIndex],
                    DataType.Scalar(ScalarKind.Integer(64)),
                    DataState.Present,
                    LongExecutionValue(fieldIndex.toLong()))
                value = builder.finish()
                check(builder.projectionCount == 0 && builder.appendCount == 1)
            }
            checksum += value.access.readLong(value.access.field(value.root, calculatedFields.last()))
        }
        blackhole = checksum.toInt()
    }


    private fun nativeBuilderLoop(rows: Int) {
        var checksum = 0L
        DefaultDataAdapterRegistry().use { registry ->
            repeat(rows) { index ->
                var value = registry.lift(BuilderReading(index.toString(), index + 0.5))
                var projections = 0
                for (fieldIndex in calculatedFields.indices) {
                    val builder = RecordOutputBuilder.open(JobValueClaim(value, exclusive = true))
                    builder.append(
                        calculatedFields[fieldIndex],
                        DataType.Scalar(ScalarKind.Integer(64)),
                        DataState.Present,
                        LongExecutionValue(fieldIndex.toLong()))
                    value = builder.finish()
                    projections += builder.projectionCount
                }
                check(projections == 1)
                checksum += value.access.readLong(value.access.field(value.root, calculatedFields.last()))
            }
        }
        blackhole = checksum.toInt()
    }


    private fun lambdaExecution(block: () -> Unit): PreparedExecution =
        object: PreparedExecution {
            override fun execute(): Outcome {
                block()
                return Outcome.Success(emptyBindings)
            }

            override fun close() = Unit
        }


    private fun printResults(results: List<BenchmarkResult>) {
        println(
            "scenario | implementation       | rows       | median ms | spread ms       | rows/s       | ratio | gc count/ms | bytes/row")
        for (result in results) {
            val reference = results.firstOrNull {
                it.scenario == result.scenario && it.implementation == result.scenario.reference
            } ?: result
            val minMillis = result.measurements.minOf { it.wallNanos } / nanosPerMillisecond
            val maxMillis = result.measurements.maxOf { it.wallNanos } / nanosPerMillisecond
            val median = result.median
            val ratio = median.rowsPerSecond / reference.median.rowsPerSecond
            println(
                "%8s | %-20s | %10d | %9.1f | %7.1f-%7.1f | %12.0f | %5.2f | %4d/%-5d | %9.1f".format(
                    result.scenario.name,
                    result.implementation,
                    result.units,
                    median.wallNanos / nanosPerMillisecond,
                    minMillis,
                    maxMillis,
                    median.rowsPerSecond,
                    ratio,
                    median.gcCollections,
                    median.gcMillis,
                    median.bytesPerUnit))
        }
    }

    private fun gcSnapshot(): GcSnapshot =
        ManagementFactory.getGarbageCollectorMXBeans().fold(GcSnapshot(0, 0)) { total, bean ->
            GcSnapshot(
                total.collections + max(bean.collectionCount, 0),
                total.millis + max(bean.collectionTime, 0))
        }


    private enum class Scenario(val reference: String) {
        S0("flat-record"),
        S1("inline"),
        S2("inline"),
        S2H("job"),
        S3("report")
    }

    private interface PreparedExecution: AutoCloseable {
        fun execute(): Outcome
    }

    private open class EngineExecution(
        private val engine: RunEngine,
        private val context: KzenAutoContext
    ): PreparedExecution {
        override fun execute(): Outcome = runBlocking {
            engine.resume()
            engine.await()
        }

        override fun close() {
            engine.close()
            context.close()
        }
    }

    private class ReportExecution(
        engine: RunEngine,
        private val context: KzenAutoContext,
        private val reportRunContext: ReportRunContext
    ): EngineExecution(engine, context) {
        fun outputInfo(): OutputInfo =
            ReportRun.outputInfoOffline(reportRunContext, context.reportWorkPool)
    }

    private class StationStats {
        var min = Double.POSITIVE_INFINITY
            private set
        var max = Double.NEGATIVE_INFINITY
            private set
        private var sum = 0.0
        private var count = 0L

        fun add(value: Double) {
            min = minOf(min, value)
            max = maxOf(max, value)
            sum += value
            count += 1
        }

        fun average(): Double = sum / count
    }

    private data class GcSnapshot(
        val collections: Long,
        val millis: Long
    )

    private data class Measurement(
        val wallNanos: Long,
        val gcCollections: Long,
        val gcMillis: Long,
        val heapBytes: Long,
        val units: Int
    ) {
        val rowsPerSecond: Double
            get() = units * nanosPerSecond / wallNanos

        val bytesPerUnit: Double
            get() = heapBytes.coerceAtLeast(0) / units.toDouble()
    }

    private data class BenchmarkResult(
        val scenario: Scenario,
        val implementation: String,
        val units: Int,
        val median: Measurement,
        val measurements: List<Measurement>
    )

    private data class BuilderReading(
        val id: String,
        val value: Double
    )
}
