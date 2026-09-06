package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.expression.JobExpressionCompiler
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.toDataContract
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/**
 * A SOURCE Worker that generates its output stream from a single Kotlin expression — THE parameterized source:
 * the Job's declared parameters are in [code]'s scope, bare by name and typed (their run-constant values read
 * via [JobControl.parameter], run-argument falling back to declared default), so `number`, `(1..number)` or
 * `listOf("a", "b")` are all valid expressions. Compiled via the shared [JobExpressionCompiler] (the same
 * contract-native service Filter and Formula use; a source has no incoming value), evaluated once,
 * and dispatched STATICALLY on the expression's INFERRED type (the same inference the editor's worker card and
 * the payload-type walk display, so the runtime always matches them): an `Iterable`, `Sequence`, `Iterator` or
 * `java.util.stream.Stream` expression is streamed element-by-element (a null value under a nullable stream
 * type is empty), any other type — including an untyped `Any` — is lifted and emitted as a single `DataValue`
 * into [output]. A blank [code] produces an empty stream.
 *
 * The output channel is UNTYPED in notation (no `of:` on the port) — the inferred payload type flows through
 * the walk instead, so any consumer is wire-compatible and still sees the static type.
 *
 * A [SourceWorker]: the framework owns end-of-stream (closing [output] once [produce] returns), batching, the
 * per-batch checkpoint, and live progress publication (the source cadence). Compilation + evaluation are heavy /
 * potentially blocking, so they run through [JobControl.runBlockingIo] to stay visible to quiescence detection,
 * and so does every pull ([SourceIngress]): the stream's closeable iterator / container and each closeable
 * element are adopted by the run inside that boundary (E9 items 1–2), closed by the run — the container when
 * the source lets it go, an element when its last holder does. Before the expression returns its stream, any
 * resource its body acquired is the author's: an expression that opens a store and throws closes it itself.
 *
 * LIVE-EDIT MIGRATION: a NON-closeable stream needs no carry of values — the rebuilt run re-evaluates the same
 * expression against the same run-constant parameters — but the STREAM POSITION must carry, or a mid-stream
 * migrate re-emits already-delivered elements while the carried ResultSink accumulation keeps the old ones
 * (duplicates). So [nextIndex] is captured / restored (the claim-before-send cursor precedent of
 * [tech.kzen.auto.server.objects.job.worker.test.GatedSourceWorker]'s source-position resume): the index is
 * claimed BEFORE [Emitter.send], because a send parked mid-flush holds its payload in the channel's in-flight
 * buffer (carried by the migration's [tech.kzen.auto.server.objects.job.channel.JobChannel.drainBuffered]) —
 * so the resumed source must not re-send it. The cursor is guarded on [code] equality: an edited expression
 * is a different stream, so it restarts. (The resumed instance re-evaluates and skips the delivered prefix,
 * which is why the expression must re-produce its elements in a stable order — trivially true of ranges,
 * lists, and any pure expression; a closeable element the re-evaluation constructs for the skipped prefix is
 * closed at once.) A CLOSEABLE stream (its container or iterator is `AutoCloseable`) is never re-opened: the
 * open iterator, with any elements pulled ahead of delivery, is DETACHED and adopted by the replacement
 * instance exactly like a reader's cursor; an edited expression closes it and starts afresh. A top-level
 * `Set` is not resumable — its iteration order is not stable across evaluations — so it restarts on any edit.
 */
@Reflect
class FormulaSourceWorker(
    output: ChannelOutput<DataValue>,

    private val code: String,
    private val selfLocation: ObjectLocation,

    @Service private val jobExpressionCompiler: JobExpressionCompiler
):
    SourceWorker(output, selfLocation)
{
    private val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
    private val sourceInput: DataContract = TypeMetadata.unit.toDataContract()
    private val sourceValue = JobDataValues.lift(Unit, sourceInput)

    // Next element index to emit; claimed BEFORE send (a send parked mid-flush holds its payload in the channel's
    // in-flight buffer, carried by the migration's drainBuffered — the resumed source must not re-send it).
    // @Volatile: written on the worker coroutine, read at the capture barrier while the worker is parked.
    @Volatile
    private var nextIndex = 0

    // The live stream and the elements pulled ahead of delivery: detached across a migration when closeable.
    private var stream: OpenedStream? = null
    private var pending: ArrayDeque<AcquiredItem> = ArrayDeque()
    private var elementContract: DataContract? = null
    private var resumable = true


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun produce(emit: Emitter, control: JobControl) {
        if (code.isBlank()) {
            return
        }

        val ingress = SourceIngress(control, selfLocation)
        val adopted = stream
        if (adopted != null) {
            // A migration handed over the open closeable stream: continue it, no re-evaluation, no skip
            drain(adopted, ingress, emit)
            return
        }

        // The declared parameters are the expression's scope (typed, bare by name); values are run-constant,
        // injected once per compiled instance (never baked into the generated source).
        val parameters = control.parameters()
        val parameterValues = parameters.definitions.map { control.parameter(it.name.value) }

        val evaluated = control.runBlockingIo {
            val attempt = jobExpressionCompiler.compile(
                selfLocation.objectPath.name.value, code,
                sourceInput, TypeMetadata.unit, classLoader, parameters)
            check(attempt.error == null) { attempt.error ?: "Unable to compile source expression" }
            val compiled = checkNotNull(attempt.compiled)
            compiled.expression.setParameters(parameterValues)
            val value = compiled.expression.evaluate(Unit, sourceValue, null)
            // Adopted inside the blocking body that produced it: a cancel winning the return cannot lose it
            val opened = if (compiled.streams) ingress.adoptStream(value) else null
            Evaluated(compiled, value, opened)
        }

        if (evaluated.compiled.streams) {
            // A null value under a nullable stream type is empty (there is nothing to iterate;
            // the static contract stays "this lane streams"). A mistyped binding never reaches here — the
            // typed parameter accessor's cast rejects it during evaluation (a run failure naming the
            // violation).
            val opened = evaluated.stream
                ?: return
            stream = opened
            elementContract = checkNotNull(evaluated.compiled.streamElementContract)
            resumable = evaluated.value !is Set<*>

            // Live-edit resume: skip the prefix the torn-down instance already delivered (stable re-evaluation
            // order — see the class kdoc). A closeable stream is never here with nextIndex > 0: it is detached.
            var skipped = 0
            while (skipped < nextIndex && opened.iterator.hasNext()) {
                ingress.discardSkipped(opened.iterator.next())
                skipped += 1
            }
            drain(opened, ingress, emit)
        }
        else if (nextIndex == 0) {
            nextIndex = 1
            emit.send(JobDataValues.lift(evaluated.value, evaluated.compiled.contract))
        }
    }


    private suspend fun drain(opened: OpenedStream, ingress: SourceIngress, emit: Emitter) {
        try {
            while (true) {
                if (pending.isEmpty()) {
                    val pulled = ingress.pull(opened.iterator, emit.batchSize())
                    if (pulled.isEmpty()) {
                        break
                    }
                    pending.addAll(pulled)
                }
                // The SourceWorker cadence checkpoints + flushes + publishes per batch, so this loop just emits.
                val item = pending.removeFirst()
                nextIndex += 1
                try {
                    emit.send(item.lift(ingress.ledger(), elementContract))
                }
                finally {
                    item.release()
                }
            }
        }
        finally {
            closeStream()
        }
    }


    private fun closeStream() {
        val open = stream ?: return
        stream = null
        val undelivered = pending
        pending = ArrayDeque()
        try {
            undelivered.forEach { it.release() }
        }
        finally {
            open.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The expression source knows its output statically: compile (cached — the same artifact produce() will
    // load) and expose the inferred type — the element type when stream-classified (the stream lane), the
    // whole type otherwise (the single-emission lane). A compile error becomes this Worker's validation error.
    override fun payloadFlow(input: JobLaneDescriptor, context: JobLaneContext): JobLaneAttempt {
        if (code.isBlank()) {
            return JobLaneAttempt(JobLaneDescriptor.unknown, null)
        }

        val attempt = jobExpressionCompiler.compile(
            selfLocation.objectPath.name.value, code,
            sourceInput, TypeMetadata.unit, context.classLoader, context.parameters)
        val compiled = attempt.compiled
            ?: return JobLaneAttempt(JobLaneDescriptor.unknown, attempt.error)
        val outputContract = if (compiled.streams) {
            checkNotNull(compiled.streamElementContract)
        }
        else {
            compiled.contract
        }
        return JobLaneAttempt(JobLaneDescriptor(outputContract), null)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("emitted" to nextIndex.toLong())


    override fun captureMigrationState(): Any {
        val open = stream
        if (open != null && open.closeable) {
            // Detach: the replacement instance continues the same iterator; the capture must not close it
            stream = null
            val carried = pending
            pending = ArrayDeque()
            return FormulaCursor(code, nextIndex, DetachedStream(open, carried, elementContract))
        }
        // A non-closeable stream is re-evaluated and skipped; a Set restarts (its order is not stable)
        return FormulaCursor(code, if (resumable) nextIndex else 0, null)
    }


    override fun loadMigrationState(captured: Any?) {
        val cursor = captured as? FormulaCursor
        if (cursor == null) {
            (captured as? AutoCloseable)?.close()
            return
        }
        // Config-changed guard: an edited expression is a different stream, so restart rather than resume —
        // and a detached closeable stream of the old expression is let go
        if (cursor.code != code) {
            cursor.close()
            return
        }
        nextIndex = cursor.nextIndex
        val detached = cursor.adoptStream()
        if (detached != null) {
            stream = detached.stream
            pending = detached.pending
            elementContract = detached.elementContract
        }
    }


    private class Evaluated(
        val compiled: JobExpressionCompiler.Compiled,
        val value: Any?,
        val stream: OpenedStream?
    )


    private class DetachedStream(
        val stream: OpenedStream,
        val pending: ArrayDeque<AcquiredItem>,
        val elementContract: DataContract?
    ) {
        fun close() {
            try {
                pending.forEach { it.release() }
            }
            finally {
                stream.close()
            }
        }
    }


    private class FormulaCursor(
        val code: String,
        val nextIndex: Int,
        private var detached: DetachedStream?
    ): AutoCloseable {
        fun adoptStream(): DetachedStream? {
            val adopted = detached
            detached = null
            return adopted
        }


        override fun close() {
            val dropped = detached ?: return
            detached = null
            dropped.close()
        }
    }
}
