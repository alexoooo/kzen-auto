package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.logic.ExpressionReturnTypeInference
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumnEval
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/**
 * A SOURCE Worker that generates its output stream from a single Kotlin expression — THE parameterized source:
 * the Job's declared parameters are in [code]'s scope, bare by name and typed (their run-constant values read
 * via [JobControl.parameter], run-argument falling back to declared default), so `number`, `(1..number)` or
 * `listOf("a", "b")` are all valid expressions. Compiled via the shared [CalculatedColumnEval] engine (the same
 * `@Service` Filter / Formula use; no columns in scope — a source has no incoming flat part), evaluated once,
 * and dispatched STATICALLY on the expression's INFERRED type (the same inference the editor's worker card and
 * the payload-type walk display, so the runtime always matches them): an `Iterable`, `Sequence`, or `Iterator`
 * expression is streamed element-by-element (a null value under a nullable stream type is empty), any other
 * type — including an untyped `Any` — is emitted as a single element, each as a payload [JobMessage] into
 * [output]. A blank [code] produces an empty stream.
 *
 * The output channel is UNTYPED in notation (no `of:` on the port) — the inferred payload type flows through
 * the walk instead, so any consumer is wire-compatible and still sees the static type.
 *
 * A [SourceWorker]: the framework owns end-of-stream (closing [output] once [produce] returns), batching, the
 * per-batch checkpoint, and live progress publication (the source cadence). Compilation + evaluation are heavy /
 * potentially blocking, so they run through [JobControl.runBlockingIo] to stay visible to quiescence detection;
 * the framework's per-batch checkpoint makes the iteration cooperatively pausable / cancellable (one step = one
 * batch).
 *
 * LIVE-EDIT MIGRATION: the *values* need no carry — the rebuilt run re-evaluates the same expression against
 * the same run-constant parameters — but the STREAM POSITION must carry, or a mid-stream migrate re-emits
 * already-delivered elements while the carried ResultSink accumulation keeps the old ones (duplicates). So
 * [nextIndex] is captured / restored (the claim-before-send cursor precedent of
 * [tech.kzen.auto.server.objects.job.worker.test.GatedSourceWorker] / [CsvReaderWorker]'s file-position
 * resume): the index is claimed BEFORE [Emitter.send], because a send parked mid-flush holds its payload in the
 * channel's in-flight buffer (carried by the migration's
 * [tech.kzen.auto.server.objects.job.channel.JobChannel.drainBuffered]) — so the resumed source must not
 * re-send it. The cursor is guarded on [code] equality: an edited expression is a different stream, so it
 * restarts. (The resumed instance re-evaluates and skips the delivered prefix, which is why the expression must
 * re-produce its elements in a stable order — trivially true of ranges, lists, and any pure expression.)
 */
@Reflect
class FormulaSourceWorker(
    output: ChannelOutput<Any?>,

    private val code: String,
    private val selfLocation: ObjectLocation,

    @Service private val calculatedColumnEval: CalculatedColumnEval
):
    SourceWorker(output, selfLocation)
{
    private val classLoader = ClassLoaderUtils.dynamicParentClassLoader()

    // Next element index to emit; claimed BEFORE send (a send parked mid-flush holds its payload in the channel's
    // in-flight buffer, carried by the migration's drainBuffered — the resumed source must not re-send it).
    // @Volatile: written on the worker coroutine, read at the capture barrier while the worker is parked.
    @Volatile
    private var nextIndex = 0


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun produce(emit: Emitter, control: JobControl) {
        if (code.isBlank()) {
            return
        }

        // The declared parameters are the expression's scope (typed, bare by name); values are run-constant,
        // injected once per compiled instance (never baked into the generated source).
        val parameters = control.parameters()
        val parameterValues = parameters.components.map { control.parameter(it.name.value) }

        val (streams, value) = control.runBlockingIo {
            val compiled = calculatedColumnEval.create(
                selfLocation.objectPath.name.value, code,
                HeaderListing.empty, TypeMetadata.anyNullable, classLoader, parameters)
            compiled.setParameters(parameterValues)

            // Strict-static dispatch: the INFERRED type alone decides stream-vs-single (never the value).
            val inferredType = calculatedColumnEval.inferredReturnKType(compiled)
            val streams = inferredType != null && ExpressionReturnTypeInference.isStreamType(inferredType)

            streams to compiled.evaluateRaw(Unit, FlatFileRecord(), HeaderListing.empty)
        }

        if (streams) {
            // A null value under a nullable stream type is empty (there is nothing to iterate;
            // the static contract stays "this lane streams"). A mistyped binding never reaches here — the
            // typed parameter accessor's cast rejects it during evaluation (a run failure naming the
            // violation).
            val iterator = ExpressionReturnTypeInference.streamIterator(value)
                ?: return

            // Live-edit resume: skip the prefix the torn-down instance already delivered (stable re-evaluation
            // order — see the class kdoc).
            var skipped = 0
            while (skipped < nextIndex && iterator.hasNext()) {
                iterator.next()
                skipped += 1
            }

            while (iterator.hasNext()) {
                // The SourceWorker cadence checkpoints + flushes + publishes per batch, so this loop just emits.
                val element = iterator.next()
                nextIndex += 1
                emit.send(JobMessage.ofPayload(element))
            }
        }
        else if (nextIndex == 0) {
            nextIndex = 1
            emit.send(JobMessage.ofPayload(value))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The expression source knows its output statically: compile (cached — the same artifact produce() will
    // load) and expose the inferred type — the element type when stream-classified (the stream lane), the
    // whole type otherwise (the single-emission lane). A compile error becomes this Worker's validation error.
    override fun payloadFlow(input: WorkerLane, context: WorkerLaneContext): WorkerLaneAttempt {
        if (code.isBlank()) {
            return WorkerLaneAttempt(WorkerLane(null, HeaderListing.empty), null)
        }

        val error = calculatedColumnEval.validate(
            selfLocation.objectPath.name.value, code,
            HeaderListing.empty, TypeMetadata.anyNullable, context.classLoader, context.parameters)
        if (error != null) {
            return WorkerLaneAttempt(WorkerLane.unknown, error)
        }

        val compiled = calculatedColumnEval.create(
            selfLocation.objectPath.name.value, code,
            HeaderListing.empty, TypeMetadata.anyNullable, context.classLoader, context.parameters)
        val inferredType = calculatedColumnEval.inferredReturnKType(compiled)
            ?: return WorkerLaneAttempt(WorkerLane(null, HeaderListing.empty), null)

        val payloadType =
            if (ExpressionReturnTypeInference.isStreamType(inferredType)) {
                ExpressionReturnTypeInference.streamElementType(inferredType)
                    ?.let(ExpressionReturnTypeInference::toTypeMetadata)
                    ?: TypeMetadata.anyNullable
            }
            else {
                ExpressionReturnTypeInference.toTypeMetadata(inferredType)
            }

        return WorkerLaneAttempt(WorkerLane(payloadType, HeaderListing.empty), null)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("emitted" to nextIndex.toLong())


    override fun captureMigrationState(): Any =
        FormulaCursor(code, nextIndex)


    override fun loadMigrationState(captured: Any?) {
        val cursor = captured as? FormulaCursor
            ?: return
        // Config-changed guard: an edited expression is a different stream, so restart rather than resume.
        if (cursor.code == code) {
            nextIndex = cursor.nextIndex
        }
    }


    private class FormulaCursor(val code: String, val nextIndex: Int)
}
