package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumnEval
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.platform.ClassNames


/**
 * A SOURCE Worker that generates its output stream from a single Kotlin expression — THE parameterized source:
 * the Job's declared parameters are in [code]'s scope, bare by name and typed (their run-constant values read
 * via [JobControl.parameter], run-argument falling back to declared default), so `number`, `(1..number)` or
 * `listOf("a", "b")` are all valid expressions. Compiled via the shared [CalculatedColumnEval] engine (the same
 * `@Service` Filter / Formula use; no columns in scope — a source has no incoming flat part), evaluated once,
 * and dispatched on the VALUE: an `Iterable<*>` result is streamed element-by-element, any other value
 * (including an unbound-parameter null) is emitted as a single element — each as a payload [JobMessage] into
 * [output]. (The element-model plan's phase 3 makes this dispatch static, from the expression's inferred type.)
 * A blank [code] produces an empty stream.
 *
 * The output channel is UNTYPED (the payload is whatever the expression yields), like RunWorker's channels —
 * the expression's type isn't carried in the channel typing, so an `of:` is omitted on the port and any
 * consumer is type-compatible.
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


    override suspend fun produce(emit: Emitter, control: JobControl) {
        if (code.isBlank()) {
            return
        }

        // The declared parameters are the expression's scope (typed, bare by name); values are run-constant,
        // injected once per compiled instance (never baked into the generated source).
        val parameters = control.parameters()
        val parameterValues = parameters.components.map { control.parameter(it.name.value) }

        val value = control.runBlockingIo {
            val compiled = calculatedColumnEval.create(
                selfLocation.objectPath.name.value, code,
                HeaderListing.empty, ClassNames.kotlinAny, classLoader, parameters)
            compiled.setParameters(parameterValues)
            compiled.evaluateRaw(Unit, FlatFileRecord(), HeaderListing.empty)
        }

        if (value is Iterable<*>) {
            val iterator = value.iterator()

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
