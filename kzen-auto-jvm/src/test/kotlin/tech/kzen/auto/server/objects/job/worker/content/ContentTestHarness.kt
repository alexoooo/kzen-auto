package tech.kzen.auto.server.objects.job.worker.content

import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.job.EngineJobControl
import tech.kzen.auto.server.exec.job.JobLogic
import tech.kzen.auto.server.exec.job.JobLogicCompiler
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingState
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.server.exec.engine.RunEngine
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.walk
import kotlin.test.assertIs


/**
 * The run harness the content streaming spike tests share (docs/plans/2026-09-16_borrowed-elements.md):
 * compiles a Job document from the test notation into a [RunEngine] over a fresh test context (optionally
 * against an edited notation, for a live-edit migration), reads a Worker's live progress off the engine, plus
 * the generated-archive fixtures under `build/content-scope/<name>/`.
 */
class ContentTestHarness: AutoCloseable {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val bigRowsEnv = "KZEN_CONTENT_SPIKE_ROWS"
        private const val defaultBigRows = 2_000_000


        fun jobLocation(document: String): ObjectLocation =
            ObjectLocation(DocumentPath.parse(document), ObjectPath.parse("main"))


        fun prepare(name: String, entries: List<Pair<String, ByteArray?>>): Path {
            val directory = Path.of("build/content-scope/$name")
            if (Files.exists(directory)) {
                directory.walk().sortedByDescending { it.nameCount }.forEach { Files.deleteIfExists(it) }
            }
            Files.createDirectories(directory)
            if (entries.isNotEmpty()) {
                writeArchive(directory.resolve("input.tar.gz"), entries)
            }
            return directory
        }


        fun writeArchive(path: Path, entries: List<Pair<String, ByteArray?>>) {
            TarArchiveOutputStream(GzipCompressorOutputStream(Files.newOutputStream(path))).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                for ((name, bytes) in entries) {
                    val entry = TarArchiveEntry(name)
                    if (bytes != null) {
                        entry.size = bytes.size.toLong()
                    }
                    tar.putArchiveEntry(entry)
                    if (bytes != null) {
                        tar.write(bytes)
                    }
                    tar.closeArchiveEntry()
                }
            }
        }


        /** One entry of [size] bytes streamed in, so a multi-GiB fixture never sits in memory. */
        fun writeLargeArchive(path: Path, name: String, size: Long, fill: (Int) -> Byte) {
            TarArchiveOutputStream(GzipCompressorOutputStream(Files.newOutputStream(path))).use { tar ->
                val entry = TarArchiveEntry(name)
                entry.size = size
                tar.putArchiveEntry(entry)
                val block = ByteArray(1024 * 1024, fill)
                var remaining = size
                while (remaining > 0) {
                    val count = minOf(remaining, block.size.toLong()).toInt()
                    tar.write(block, 0, count)
                    remaining -= count
                }
                tar.closeArchiveEntry()
            }
        }


        /**
         * `big.csv` of `id,value` rows in `build/content-scope/take-out/input.tar.gz`, generated once per build
         * (row count from `KZEN_CONTENT_SPIKE_ROWS`); returns the row count.
         */
        fun prepareBig(): Int =
            prepareCsvArchive("take-out", System.getenv(bigRowsEnv)?.toIntOrNull() ?: defaultBigRows)


        /** `big.csv` of the same rows as [prepareBig], as a plain file in `build/content-scope/<name>/`. */
        fun prepareBigFile(name: String): Int =
            prepareCsvFile(name, System.getenv(bigRowsEnv)?.toIntOrNull() ?: defaultBigRows)


        /** `big.csv` of [rows] `id,value` rows as a plain file in `build/content-scope/<name>/`. */
        fun prepareCsvFile(name: String, rows: Int): Int {
            val directory = Path.of("build/content-scope/$name")
            Files.createDirectories(directory)
            val csv = directory.resolve("big.csv")
            val marker = directory.resolve("rows.txt")
            if (Files.exists(csv) && Files.exists(marker) && Files.readString(marker) == rows.toString()) {
                return rows
            }
            Files.newBufferedWriter(csv).use { writer -> writeRows(writer, rows) }
            Files.writeString(marker, rows.toString())
            return rows
        }


        /** `big.csv` of [rows] `id,value` rows in `build/content-scope/<name>/input.tar.gz`, generated once per
         *  build. */
        fun prepareCsvArchive(name: String, rows: Int): Int {
            val directory = Path.of("build/content-scope/$name")
            Files.createDirectories(directory)
            val archive = directory.resolve("input.tar.gz")
            val marker = directory.resolve("rows.txt")
            if (Files.exists(archive) && Files.exists(marker) && Files.readString(marker) == rows.toString()) {
                return rows
            }

            val csv = directory.resolve("big.csv")
            Files.newBufferedWriter(csv).use { writer -> writeRows(writer, rows) }
            TarArchiveOutputStream(GzipCompressorOutputStream(Files.newOutputStream(archive))).use { tar ->
                val entry = TarArchiveEntry("big.csv")
                entry.size = Files.size(csv)
                tar.putArchiveEntry(entry)
                Files.newInputStream(csv).use { it.transferTo(tar) }
                tar.closeArchiveEntry()
            }
            Files.deleteIfExists(csv)
            Files.writeString(marker, rows.toString())
            return rows
        }


        private fun writeRows(writer: BufferedWriter, rows: Int) {
            writer.write("id,value\n")
            for (i in 0 until rows) {
                writer.write(i.toString())
                writer.write(",")
                writer.write((i * 2L).toString())
                writer.write("\n")
            }
        }


        fun listFiles(root: Path): Set<String> {
            if (!Files.exists(root)) return emptySet()
            return root.walk()
                .filter { Files.isRegularFile(it) }
                .map { root.relativize(it).toString().replace('\\', '/') }
                .toSet()
        }


        /** The `main` result of a `ResultSinkWorker keep: all`, as boundary values. */
        @Suppress("UNCHECKED_CAST")
        fun collected(outcome: Outcome): List<Any?> {
            val success = assertIs<Outcome.Success>(outcome)
            val bound = assertIs<BindingState.Bound>(success.value[BindingName("main")])
            return JobDataValues.boundary(bound.value) as List<Any?>
        }


        /** [notation] with one attribute of one object replaced (the live edit under test). */
        fun edit(
            notation: GraphNotation,
            objectLocation: ObjectLocation,
            attribute: String,
            value: AttributeNotation
        ): GraphNotation =
            NotationReducer()
                .applyStructural(
                    notation,
                    UpsertAttributeCommand(objectLocation, AttributeName(attribute), value))
                .graphNotation


        fun edit(notation: GraphNotation, objectLocation: ObjectLocation, attribute: String, value: String) =
            edit(notation, objectLocation, attribute, ScalarAttributeNotation(value))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var context: KzenAutoContext? = null


    fun run(document: String): Outcome {
        val engine = start(document)
        return try {
            runBlocking {
                engine.resume()
                engine.await()
            }
        }
        finally {
            engine.close()
        }
    }


    fun start(document: String): RunEngine {
        fresh()
        val location = jobLocation(document)
        return engine(compile(location, AutoTestUtils.readNotation()), location)
    }


    /** A fresh test context, replacing (and closing) the previous one. */
    fun fresh(): KzenAutoContext {
        context?.close()
        val created = KzenAutoContext.forTest()
        context = created
        return created
    }


    fun compile(jobLocation: ObjectLocation, graphNotation: GraphNotation): JobLogic {
        val current = context ?: fresh()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful
        return JobLogicCompiler.compile(
            jobLocation, graphNotation, graphDefinition,
            LogicCompilerServices(
                current.graphEnvironment,
                current.objectStableMapper,
                current.cachedKotlinCompiler,
                current.scriptValidationCache,
                current.jobValidationCache,
                current.notationMetadataReader,
                current.jobWorkPool,
                LogicRunExecutionId.random()))
    }


    fun engine(logic: JobLogic, jobLocation: ObjectLocation): RunEngine {
        val current = checkNotNull(context) { "Compile before starting an engine" }
        return RunEngine(logic, current.objectStableMapper.objectStableId(jobLocation))
    }


    /** A Worker's live progress value, read off its node's progress emit (the path the Job UI polls); null if none. */
    fun workerProgress(engine: RunEngine, workerLocation: ObjectLocation, key: String): Any? {
        val current = checkNotNull(context) { "No context" }
        val workerStableId = current.objectStableMapper.objectStableId(workerLocation)
        val workerNode = engine.snapshot().root.children
            .firstOrNull { it.stableId == workerStableId }
            ?: return null
        val progress = workerNode.live[Address.of(EngineJobControl.workerProgressAddressMarker)]?.get() as? Map<*, *>
            ?: return null
        return progress[key]
    }


    override fun close() {
        context?.close()
        context = null
    }
}
