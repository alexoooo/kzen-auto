package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.script.step.eval.StepExpression
import tech.kzen.auto.server.objects.script.step.eval.StepExpressionCompiler
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/**
 * A SOURCE Worker that generates its output stream from a single Kotlin expression. The user's [code] is an
 * arbitrary Kotlin expression evaluating to an `Iterable<*>` (e.g. `(1..100)`, `listOf("a", "b")`, a generated
 * sequence); this Worker compiles it once via the genuine [StepExpressionCompiler] / [CachedKotlinCompiler]
 * path (the same engine [tech.kzen.auto.server.objects.script.step.eval.FormulaStep] uses, injected as a
 * `@Service`), evaluates it, and iterates the result, sending each element individually into [output].
 *
 * The output channel is UNTYPED (each element is whatever the expression yields, `Any?`), like RunWorker's
 * channels — the iterable's element type isn't carried in the channel typing, so an `of:` is omitted on the
 * port and any consumer is type-compatible.
 *
 * A [SourceWorker]: the framework owns end-of-stream (closing [output] once [produce] returns), batching, the
 * per-chunk checkpoint, and live progress publication (the source cadence). Compilation + evaluation are heavy /
 * potentially blocking, so they run through [JobControl.runBlockingIo] to stay visible to quiescence detection;
 * the framework's per-chunk checkpoint makes the iteration cooperatively pausable / cancellable (one step = one
 * chunk).
 *
 * No state migration: a pause / edit-config / continue restarts the source from scratch (re-evaluates the
 * expression and re-iterates from the top), the safe default — coherent for a pure expression that reproduces
 * its elements, and what a non-opted-in [WorkerBase] does anyway.
 */
@Reflect
class FormulaSourceWorker(
    output: ChannelOutput<Any?>,

    private val code: String,
    private val selfLocation: ObjectLocation,

    @Service private val cachedKotlinCompiler: CachedKotlinCompiler
):
    SourceWorker<Any?>(output, selfLocation)
{
    private var emitted = 0L


    override suspend fun produce(emit: Emitter<Any?>, control: JobControl) {
        val iterable = control.runBlockingIo { evaluate() }

        for (element in iterable) {
            // The SourceWorker cadence checkpoints + flushes + publishes per chunk, so this loop just emits: one
            // step surfaces one chunk downstream (cooperative pause / cancel land per chunk).
            emit.send(element)
            emitted += 1
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Compiles the user expression into a StepExpression returning Iterable<*> (no in-scope values — a source
    // has no predecessors), loads + instantiates it, and evaluates it to the iterable to stream.
    private fun evaluate(): Iterable<*> {
        val classLoader = ClassLoaderUtils.dynamicParentClassLoader()

        val mainClassName = "Source_" + StepExpressionCompiler.sanitizeName(selfLocation.objectPath.name.value)
        val generatedCode = StepExpressionCompiler.generateCode(
            mainClassName, "Iterable<*>", code, mapOf())

        val error = cachedKotlinCompiler.tryCompile(generatedCode, classLoader)
        check(error == null) {
            "Unable to compile: $error - $generatedCode"
        }

        val clazz = cachedKotlinCompiler.tryLoad(generatedCode, classLoader)
        check(clazz != null) {
            "Unable to load: $generatedCode"
        }

        @Suppress("UNCHECKED_CAST")
        val expression = (clazz as Class<StepExpression>).getDeclaredConstructor().newInstance()

        return expression.evaluate(listOf()) as Iterable<*>
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("emitted" to emitted)
}
