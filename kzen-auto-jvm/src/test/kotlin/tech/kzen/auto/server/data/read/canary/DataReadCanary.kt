package tech.kzen.auto.server.data.read.canary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.api.DataSource
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.schema.RecordSchema
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.data.ConfiguredDataOpener
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.data.SchemaCache
import tech.kzen.auto.server.data.read.delimited.ConfiguredDelimitedReaderCapability
import tech.kzen.auto.server.objects.datasource.FileDataSource
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedFormat
import tech.kzen.auto.server.objects.job.channel.JobChannel
import tech.kzen.auto.server.objects.job.worker.data.DataReadCore
import tech.kzen.auto.server.objects.job.worker.data.ReadWorker
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionResolution
import tech.kzen.auto.server.service.plugin.HostReportDefinitionRepository
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.util.digest.Digest
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max


object DataReadCanary {
    private const val propertyPrefix = "dataReadCanary."
    private const val nanosPerSecond = 1_000_000_000.0
    private const val nanosPerMillisecond = 1_000_000.0
    private const val fnvOffsetBasis = -3750763034362895579L
    private const val fnvPrime = 1099511628211L

    private val workerLocation = ObjectLocation.parse("test/data-read-canary.yaml#main.workers/read")
    private val sourceLocation = ObjectLocation.parse("test/data-read-canary.yaml#main.sources/input")


    @JvmStatic
    fun main(args: Array<String>) {
        if (args.any { it == "--help" }) {
            printUsage()
            return
        }
        val supplied = Arguments(args)
        val path = supplied.optional("path")
        if (path == null) {
            println("DATA-READ CANARY SKIPPED: '${propertyPrefix}path' was not supplied")
            return
        }

        val config = CanaryConfig.parse(path, supplied)
        require(Files.isRegularFile(config.path)) {
            "Configured canary input is not a regular file: ${config.path}"
        }
        val cacheDirectory = Files.createTempDirectory("data-read-canary-cache")
        try {
            execute(config, cacheDirectory)
        }
        finally {
            WorkUtils.deleteDirThrowing(cacheDirectory)
        }
    }


    private fun printUsage() {
        println(
            """
            Run with -D$propertyPrefix<name>=<value> or --args="--name=value ...".
            Required when present: path, delimiter, header=present|absent,
            schemaFields=name:Kind,..., numericField, expectedRowCount,
            expectedFinalRecord as a JSON array, and expectedChecksum as unsigned FNV-1a-64 hex.
            Supported schema kinds: Text, Boolean, Int8/16/32/64, Float32/64, Decimal; append ? for nullable.
            numericField must be a non-null Integer, Floating, or Decimal field; Decimal aggregates exactly.
            Checksum input is each canonical typed field prefixed by 0/1 for null/present and followed by FF,
            with FE after each record. Optional controls include character/dialect settings,
            runs, warmups, batchSize, channelCapacity, minimumRowsPerSecond,
            maximumRegressionPercent, parserBaselineRowsPerSecond, and jobBaselineRowsPerSecond.
            Use -PdataReadCanaryMaxHeap to set the forked process memory ceiling; heap and GC are observations.
            """.trimIndent())
    }


    private fun execute(config: CanaryConfig, cacheDirectory: Path) {
        val repository = HostReportDefinitionRepository(emptyList())
        val listing = FileListingAction(repository)
        val localSource = FileDataSource(
            "",
            "",
            listOf(mapOf(FileSelectionEntry.locationKey to config.path.toString())),
            config.format,
            "",
            FileDataSource.missingFail,
            listing)
        val opener = ConfiguredDataOpener(SchemaCache(WorkUtils(cacheDirectory)))
        val part = runBlocking {
            localSource.resolve(DirectDataContext).manifest.units.single().parts.single()
        }
        check(part.expectedFingerprint != null) { "Local source did not stamp a content fingerprint" }
        check(part.resolvedRead == config.format.resolvedRead(part.ref)) {
            "Resolved reader snapshot differs from the configured format"
        }
        check(part.resolvedRead.reader == ConfiguredDelimitedReaderCapability.identity) {
            "Configured source resolved an unexpected reader: ${part.resolvedRead.reader}"
        }
        val expectedCoding = if (part.ref.id.lowercase().endsWith(".gz")) {
            ContentCodingSpec.gzip
        }
        else {
            ContentCodingSpec.identity
        }
        check(part.resolvedRead.contentCodings == listOf(expectedCoding)) {
            "Resolved content coding ${part.resolvedRead.contentCodings} did not match $expectedCoding"
        }
        println(
            "Timing scopes: configured-reader includes content open and typed row consumption over one " +
                    "pre-resolved part; Job end-to-end includes source resolution, ReadWorker, and JobChannel. " +
                    "Strict declared shape avoids inspection-cache reads in both scopes.")

        repeat(config.warmups) {
            verify(config, parse(opener, part, config), "parser warmup ${it + 1}")
            verify(config, runWorker(opener, localSource, config), "Job warmup ${it + 1}")
        }
        val parser = mutableListOf<Measurement>()
        val job = mutableListOf<Measurement>()
        repeat(config.runs) {
            parser += measure("parser", config) { parse(opener, part, config) }
            job += measure("Job", config) { runWorker(opener, localSource, config) }
        }

        val parserMedian = report("configured-reader", parser, config.expectedRows)
        val jobMedian = report("Job end-to-end", job, config.expectedRows)
        check(parserMedian.numericAggregate == jobMedian.numericAggregate) {
            "Parser and Job typed numeric aggregates differ: " +
                    "${parserMedian.numericAggregate} vs ${jobMedian.numericAggregate}"
        }
        enforceThroughput(config, parserMedian.rowsPerSecond, jobMedian.rowsPerSecond)
        println(
            "DATA-READ CANARY PASSED: rows=${config.expectedRows}, " +
                    "checksum=${config.expectedChecksum.toULong().toString(16)}, " +
                    "numeric-aggregate=${parserMedian.numericAggregate}")
    }


    private fun parse(
        opener: ConfiguredDataOpener,
        part: DataPart,
        config: CanaryConfig
    ): StreamResult = runBlocking {
        val accumulator = StreamAccumulator(config.fields, config.numericField)
        opener.open(DirectDataContext, part).use { cursor ->
            while (cursor.hasNext()) {
                accumulator.accept(cursor.next())
            }
        }
        accumulator.finish()
    }


    private fun runWorker(
        opener: ConfiguredDataOpener,
        source: DataSource,
        config: CanaryConfig
    ): StreamResult = runBlocking {
        val channel = JobChannel(config.channelCapacity, config.batchSize)
        val worker = ReadWorker(
            channel.newProducer(),
            ObjectReference.parse("input"),
            ReadWorker.emitItems,
            DataRole.main.name,
            ReadWorker.attributesIgnore,
            workerLocation,
            DataOpenerLookup(opener),
            DataReadCore.schemaStrict)
        worker.loadSourceResolution(WorkerDefinitionResolution.Resolved(
            sourceLocation,
            Digest.ofUtf8("data-read-canary-source"),
            source))

        coroutineScope {
            val producer = async(Dispatchers.Default) {
                worker.run(CanaryJobControl)
            }
            val accumulator = StreamAccumulator(config.fields, config.numericField)
            while (true) {
                val batch = channel.input.receiveBatch() ?: break
                batch.forEach(accumulator::accept)
            }
            producer.await()
            accumulator.finish()
        }
    }


    private fun measure(
        label: String,
        config: CanaryConfig,
        operation: () -> StreamResult
    ): Measurement {
        val heapPools = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }
        heapPools.forEach { it.resetPeakUsage() }
        val memory = ManagementFactory.getMemoryMXBean()
        val heapBefore = memory.heapMemoryUsage.used
        val gcBefore = gcSnapshot()
        val started = System.nanoTime()
        val result = operation()
        val elapsed = System.nanoTime() - started
        val gcAfter = gcSnapshot()
        val heapAfter = memory.heapMemoryUsage.used
        val peakHeap = heapPools.sumOf { max(it.peakUsage.used, 0L) }
        val peakGrowth = max(peakHeap - heapBefore, 0L)
        verify(config, result, label)
        return Measurement(
            elapsed,
            heapAfter - heapBefore,
            peakGrowth,
            gcAfter.collections - gcBefore.collections,
            gcAfter.millis - gcBefore.millis,
            result.numericAggregate)
    }


    private fun verify(config: CanaryConfig, result: StreamResult, label: String) {
        check(result.rows == config.expectedRows) {
            "$label row count ${result.rows} did not match ${config.expectedRows}"
        }
        check(result.finalRecord == config.expectedFinalRecord) {
            "$label final record ${result.finalRecord} did not match ${config.expectedFinalRecord}"
        }
        check(result.checksum == config.expectedChecksum) {
            "$label checksum ${result.checksum.toULong().toString(16)} did not match " +
                    config.expectedChecksum.toULong().toString(16)
        }
        check(result.numericReads == config.expectedRows) {
            "$label typed numeric reads ${result.numericReads} did not match ${config.expectedRows}"
        }
    }


    private fun report(label: String, measurements: List<Measurement>, rows: Long): MeasurementSummary {
        val orderedByWall = measurements.sortedBy { it.wallNanos }
        val medianWall = orderedByWall[orderedByWall.size / 2]
        val minimumMillis = orderedByWall.first().wallNanos / nanosPerMillisecond
        val maximumMillis = orderedByWall.last().wallNanos / nanosPerMillisecond
        val medianHeapDelta = measurements.map { it.heapDeltaBytes }.sorted()[measurements.size / 2]
        val medianPeak = measurements.map { it.peakHeapGrowthBytes }.sorted()[measurements.size / 2]
        val medianGcCollections = measurements.map { it.gcCollections }.sorted()[measurements.size / 2]
        val medianGcMillis = measurements.map { it.gcMillis }.sorted()[measurements.size / 2]
        val summary = MeasurementSummary(
            medianWall.wallNanos,
            medianHeapDelta,
            medianPeak,
            medianGcCollections,
            medianGcMillis,
            medianWall.numericAggregate,
            rows)
        println(
            "$label: median=${"%.1f".format(summary.wallNanos / nanosPerMillisecond)} ms, " +
                    "spread=${"%.1f".format(minimumMillis)}-${"%.1f".format(maximumMillis)} ms, " +
                    "rows/s=${"%.0f".format(summary.rowsPerSecond)}, " +
                    "heap-delta=${summary.heapDeltaBytes}, peak-heap-growth=${summary.peakHeapGrowthBytes}, " +
                    "gc=${summary.gcCollections}/${summary.gcMillis} ms")
        return summary
    }


    private fun enforceThroughput(config: CanaryConfig, parser: Double, job: Double) {
        config.minimumRowsPerSecond?.let { minimum ->
            check(parser >= minimum) { "Parser throughput $parser rows/s is below $minimum" }
            check(job >= minimum) { "Job throughput $job rows/s is below $minimum" }
        }
        config.maximumRegressionPercent?.let { maximumRegression ->
            val retained = 1.0 - maximumRegression / 100.0
            val parserFloor = requireNotNull(config.parserBaselineRowsPerSecond) * retained
            val jobFloor = requireNotNull(config.jobBaselineRowsPerSecond) * retained
            check(parser >= parserFloor) {
                "Parser throughput $parser rows/s is below regression floor $parserFloor"
            }
            check(job >= jobFloor) {
                "Job throughput $job rows/s is below regression floor $jobFloor"
            }
        }
    }


    private fun gcSnapshot(): GcSnapshot =
        ManagementFactory.getGarbageCollectorMXBeans().fold(GcSnapshot(0, 0)) { total, bean ->
            GcSnapshot(
                total.collections + max(bean.collectionCount, 0),
                total.millis + max(bean.collectionTime, 0))
        }


    private class Arguments(args: Array<String>) {
        private val values = buildMap {
            for (propertyName in System.getProperties().stringPropertyNames()) {
                if (propertyName.startsWith(propertyPrefix)) {
                    put(propertyName.removePrefix(propertyPrefix), System.getProperty(propertyName))
                }
            }
            for (argument in args) {
                require(argument.startsWith("--") && '=' in argument) {
                    "Canary arguments must use --name=value: $argument"
                }
                val separator = argument.indexOf('=')
                put(argument.substring(2, separator), argument.substring(separator + 1))
            }
        }

        fun required(name: String): String = optional(name)
            ?: throw IllegalArgumentException("Missing '$propertyPrefix$name' or '--$name=...'")

        fun optional(name: String): String? = values[name]?.takeIf { it.isNotBlank() }

        fun int(name: String, default: Int): Int {
            val value = optional(name) ?: return default
            return value.toIntOrNull() ?: error("$name must be an integer")
        }

        fun double(name: String): Double? = optional(name)?.let {
            it.toDoubleOrNull() ?: error("$name must be a number")
        }
    }


    private data class CanaryConfig(
        val path: Path,
        val format: ConfiguredDelimitedFormat,
        val fields: List<FieldSpec>,
        val numericField: FieldSpec,
        val expectedRows: Long,
        val expectedFinalRecord: List<String?>,
        val expectedChecksum: Long,
        val runs: Int,
        val warmups: Int,
        val batchSize: Int,
        val channelCapacity: Int,
        val minimumRowsPerSecond: Double?,
        val maximumRegressionPercent: Double?,
        val parserBaselineRowsPerSecond: Double?,
        val jobBaselineRowsPerSecond: Double?
    ) {
        companion object {
            fun parse(pathText: String, supplied: Arguments): CanaryConfig {
                val path = Path.of(pathText).toAbsolutePath().normalize()
                val fields = parseFields(supplied.required("schemaFields"))
                val numericName = supplied.required("numericField")
                val numericField = fields.singleOrNull { it.id.name == numericName }
                    ?: error("numericField '$numericName' must name exactly one schema field")
                require(
                    !numericField.nullable &&
                            (numericField.kind is ScalarKind.Integer ||
                                    numericField.kind is ScalarKind.Floating ||
                                    numericField.kind == ScalarKind.Decimal)
                ) {
                    "numericField must use a non-null Integer, Floating, or Decimal kind"
                }
                val schema = DataContract(DataType.Record(fields.map {
                    DataField(it.id, DataType.Scalar(it.kind, it.nullable))
                }))
                val delimiter = decodeCharacter(supplied.required("delimiter"), "delimiter")
                val header = supplied.required("header")
                require(header == "present" || header == "absent") {
                    "header must be 'present' or 'absent' when a typed schema is supplied"
                }
                val quote = when (val configured = supplied.optional("quote")) {
                    null -> "\""
                    "none" -> ""
                    else -> decodeCharacter(configured, "quote")
                }
                val format = ConfiguredDelimitedFormat(
                    "Configured canary input",
                    emptyList(),
                    false,
                    delimiter,
                    quote,
                    supplied.optional("escape") ?: "double-quote",
                    supplied.optional("recordSeparator") ?: "lf",
                    supplied.optional("trimming") ?: "none",
                    header,
                    supplied.optional("charset") ?: "UTF-8",
                    supplied.optional("bom") ?: "permit",
                    supplied.optional("malformed") ?: "report",
                    supplied.optional("unmappable") ?: "report",
                    supplied.optional("nullToken") ?: "",
                    CanaryRecordSchema(schema))
                val expectedRows = supplied.required("expectedRowCount").toLong()
                require(expectedRows > 0) { "expectedRowCount must be positive" }
                val expectedFinal = Json.parseToJsonElement(
                    supplied.required("expectedFinalRecord")).jsonArray.map {
                    if (it is JsonNull) null else it.jsonPrimitive.content
                }
                require(expectedFinal.size == fields.size) {
                    "expectedFinalRecord has ${expectedFinal.size} values; schema has ${fields.size} fields"
                }
                val expectedChecksum = parseChecksum(supplied.required("expectedChecksum"))
                val runs = supplied.int("runs", 3)
                val warmups = supplied.int("warmups", 1)
                val batchSize = supplied.int("batchSize", 1024)
                val channelCapacity = supplied.int("channelCapacity", 4)
                require(runs > 0) { "runs must be positive" }
                require(warmups >= 0) { "warmups must not be negative" }
                require(batchSize > 0) { "batchSize must be positive" }
                require(channelCapacity >= 0) { "channelCapacity must not be negative" }
                val minimumRowsPerSecond = supplied.double("minimumRowsPerSecond")
                val maximumRegression = supplied.double("maximumRegressionPercent")
                val parserBaseline = supplied.double("parserBaselineRowsPerSecond")
                val jobBaseline = supplied.double("jobBaselineRowsPerSecond")
                require(minimumRowsPerSecond == null || minimumRowsPerSecond > 0) {
                    "minimumRowsPerSecond must be positive"
                }
                if (maximumRegression != null) {
                    require(maximumRegression in 0.0..100.0) {
                        "maximumRegressionPercent must be between 0 and 100"
                    }
                    require(parserBaseline != null && parserBaseline > 0 && jobBaseline != null && jobBaseline > 0) {
                        "maximumRegressionPercent requires positive parser and Job baseline rows/sec"
                    }
                }
                return CanaryConfig(
                    path,
                    format,
                    fields,
                    numericField,
                    expectedRows,
                    expectedFinal,
                    expectedChecksum,
                    runs,
                    warmups,
                    batchSize,
                    channelCapacity,
                    minimumRowsPerSecond,
                    maximumRegression,
                    parserBaseline,
                    jobBaseline)
            }


            private fun parseFields(encoded: String): List<FieldSpec> {
                val fields = encoded.split(',').map { entry ->
                    val separator = entry.lastIndexOf(':')
                    require(separator > 0 && separator < entry.lastIndex) {
                        "schemaFields entries must use name:Kind: $entry"
                    }
                    val name = entry.substring(0, separator).trim()
                    var kindName = entry.substring(separator + 1).trim()
                    val nullable = kindName.endsWith('?')
                    if (nullable) kindName = kindName.dropLast(1)
                    FieldSpec(FieldId(name), parseKind(kindName), nullable)
                }
                require(fields.isNotEmpty()) { "schemaFields must not be empty" }
                require(fields.map { it.id.name }.distinct().size == fields.size) {
                    "schemaFields names must be unique"
                }
                return fields
            }


            private fun parseKind(name: String): ScalarKind = when (name.lowercase()) {
                "text" -> ScalarKind.Text
                "boolean" -> ScalarKind.Boolean
                "int8", "integer8" -> ScalarKind.Integer(8)
                "int16", "integer16" -> ScalarKind.Integer(16)
                "int32", "integer32" -> ScalarKind.Integer(32)
                "int64", "integer64", "integer" -> ScalarKind.Integer(64)
                "float32", "floating32" -> ScalarKind.Floating(32)
                "float64", "floating64", "floating" -> ScalarKind.Floating(64)
                "decimal" -> ScalarKind.Decimal
                else -> throw IllegalArgumentException("Unsupported schema kind: $name")
            }


            private fun decodeCharacter(value: String, label: String): String {
                val decoded = when {
                    value == "\\t" -> "\t"
                    value == "\\r" -> "\r"
                    value == "\\n" -> "\n"
                    value.startsWith("\\u") && value.length == 6 ->
                        value.substring(2).toInt(16).toChar().toString()
                    else -> value
                }
                require(decoded.length == 1) { "$label must decode to exactly one character" }
                return decoded
            }


            private fun parseChecksum(value: String): Long {
                val hex = value.removePrefix("0x").removePrefix("0X")
                return hex.toULongOrNull(16)?.toLong()
                    ?: throw IllegalArgumentException(
                        "expectedChecksum must be an unsigned hexadecimal value")
            }
        }
    }


    private data class FieldSpec(
        val id: FieldId,
        val kind: ScalarKind,
        val nullable: Boolean
    )


    private class CanaryRecordSchema(
        private val dataContract: DataContract
    ): RecordSchema {
        override fun contract(): DataContract = dataContract
    }


    private class StreamAccumulator(
        private val fields: List<FieldSpec>,
        private val numericField: FieldSpec
    ) {
        private var checksum = fnvOffsetBasis
        private var rows = 0L
        private var finalRecord = emptyList<String?>()
        private var numericReads = 0L
        private var numericAggregate = BigDecimal.ZERO


        fun accept(value: DataValue) {
            val canonical = fields.map { field ->
                val node = value.access.field(value.root, field.id)
                when (value.access.state(node)) {
                    DataState.Absent -> error("Configured reader emitted absent field ${field.id}")
                    DataState.Null -> null
                    DataState.Present -> canonicalScalar(value, node, field.kind)
                }
            }
            for (field in canonical) {
                checksum = update(checksum, if (field == null) 0 else 1)
                if (field != null) {
                    for (byte in field.toByteArray(StandardCharsets.UTF_8)) {
                        checksum = update(checksum, byte.toInt() and 0xff)
                    }
                }
                checksum = update(checksum, 0xff)
            }
            checksum = update(checksum, 0xfe)

            val numericNode = value.access.field(value.root, numericField.id)
            if (value.access.state(numericNode) == DataState.Present) {
                when (numericField.kind) {
                    is ScalarKind.Integer -> {
                        numericAggregate += BigDecimal.valueOf(value.access.readLong(numericNode))
                    }
                    is ScalarKind.Floating -> {
                        val next = value.access.readDouble(numericNode)
                        check(next.isFinite()) { "numericField contains a non-finite floating value: $next" }
                        numericAggregate += BigDecimal.valueOf(next)
                    }
                    ScalarKind.Decimal -> {
                        numericAggregate += BigDecimal(decimalText(value, numericNode))
                    }
                    else -> error("Configured numeric field is not numeric: ${numericField.kind}")
                }
                numericReads += 1
            }
            rows += 1
            finalRecord = canonical
        }


        fun finish(): StreamResult =
            StreamResult(
                rows,
                finalRecord,
                checksum,
                numericReads,
                "decimal:${numericAggregate.toPlainString()}")


        private fun canonicalScalar(value: DataValue, node: DataNode, kind: ScalarKind): String =
            when (kind) {
                ScalarKind.Boolean -> value.access.readBoolean(node).toString()
                is ScalarKind.Integer -> value.access.readLong(node).toString()
                is ScalarKind.Floating -> value.access.readDouble(node).toString()
                ScalarKind.Decimal -> decimalText(value, node)
                else -> value.access.readText(node)
            }


        private fun decimalText(value: DataValue, node: DataNode): String =
            (value.access.scalar(node) as? TextExecutionValue)?.value
                ?: error("Decimal field is not backed by canonical text")


        private fun update(current: Long, byte: Int): Long =
            (current xor byte.toLong()) * fnvPrime
    }


    private object DirectDataContext: DataContext {
        override fun argument(name: String): Any? = null

        override suspend fun <R> blocking(block: () -> R): R =
            withContext(Dispatchers.IO) { block() }
    }


    private object CanaryJobControl: JobControl {
        override suspend fun checkpoint() = Unit

        override suspend fun <R> runBlockingIo(block: () -> R): R =
            withContext(Dispatchers.IO) { block() }

        override fun scratchDir(): String =
            throw UnsupportedOperationException("Canary reader needs no scratch directory")

        override fun publishProgress(
            location: ObjectLocation,
            value: Map<String, Any?>,
            force: Boolean
        ) = Unit

        override suspend fun host(instructions: ObjectLocation, input: Any?): DataBindings =
            throw UnsupportedOperationException("Canary reader hosts no child")
    }


    private data class StreamResult(
        val rows: Long,
        val finalRecord: List<String?>,
        val checksum: Long,
        val numericReads: Long,
        val numericAggregate: String
    )


    private data class GcSnapshot(
        val collections: Long,
        val millis: Long
    )


    private data class Measurement(
        val wallNanos: Long,
        val heapDeltaBytes: Long,
        val peakHeapGrowthBytes: Long,
        val gcCollections: Long,
        val gcMillis: Long,
        val numericAggregate: String
    )


    private data class MeasurementSummary(
        val wallNanos: Long,
        val heapDeltaBytes: Long,
        val peakHeapGrowthBytes: Long,
        val gcCollections: Long,
        val gcMillis: Long,
        val numericAggregate: String,
        val rows: Long
    ) {
        val rowsPerSecond: Double
            get() = rows * nanosPerSecond / wallNanos
    }
}
