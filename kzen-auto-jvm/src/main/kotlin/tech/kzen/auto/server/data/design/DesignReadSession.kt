package tech.kzen.auto.server.data.design

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.objects.datasource.DesignDataContext
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.util.digest.Digest
import kotlin.time.TimeSource


/**
 * One validation pass's reading, bounded by [budget] and shared by every Worker of the pass: each [read] runs within
 * what is left of the deadline, and a read the deadline cuts short marks the pass [limited]. [evidence] is what the
 * pass looked at, so its result is reused only while the data is unchanged.
 *
 * The value limit is a fixed bound, not a cut: a lane longer than it offers its first values, and says so, in every
 * pass alike. A read blocks the calling thread, as the rest of validation does (its expression compiles); the
 * deadline is checked between suspensions, so one blocking inspection already begun completes before the read gives
 * up.
 */
class DesignReadSession internal constructor(
    private val reader: DesignReader,
    val budget: DesignReadBudget
) {
    //-----------------------------------------------------------------------------------------------------------------
    private val started = TimeSource.Monotonic.markNow()
    private val context: DataContext = DesignDataContext(ExecutionRequest(RequestParams.empty, null))
    private val recorded = mutableListOf<DesignEvidence>()

    val evidence: List<DesignEvidence>
        get() = recorded

    /** True when a limit cut this pass's reading short, so what it typed is partial. */
    var limited = false
        private set


    //-----------------------------------------------------------------------------------------------------------------
    fun record(evidence: DesignEvidence) {
        recorded.add(evidence)
    }


    /**
     * A [read] of [block] whose result the pass depends on: records it as evidence under [subject], digested by
     * [digest], whose recheck runs [block] again (outside the budget: a cache hit's check is one read per piece of
     * evidence, never a pass).
     */
    fun <T> observe(
        subject: String,
        block: suspend (DataContext) -> T,
        digest: (T) -> Digest
    ): Read<T>? {
        val result = read(block)
            ?: return null
        record(DesignEvidence(subject, digest(result.value)) {
            runBlocking { digest(block(context)) }
        })
        return result
    }


    /** Runs [block] within the deadline's remainder; null (and [limited]) when the deadline cuts it short. */
    fun <T> read(block: suspend (DataContext) -> T): Read<T>? {
        val remaining = budget.deadline - started.elapsedNow()
        if (!remaining.isPositive()) {
            limited = true
            return null
        }
        val result = runBlocking {
            withTimeoutOrNull(remaining) {
                Read(block(context))
            }
        }
        if (result == null) {
            limited = true
        }
        return result
    }


    class Read<T>(val value: T)


    //-----------------------------------------------------------------------------------------------------------------
    /** The shape of [part], inspected once per fingerprinted content. */
    suspend fun inspectShape(context: DataContext, openerLookup: DataOpenerLookup, part: DataPart): DataShape? {
        val key = part.takeIf { it.expectedFingerprint != null }?.digest()
        key?.let(reader::cachedShape)?.let { return it }
        val shape = openerLookup.openerFor(part.ref).inspectShape(context, part)
            ?: return null
        key?.let { reader.storeShape(it, shape) }
        return shape
    }


    /** How [format] reads [ref], resolved once per fingerprinted content (automatic detection samples it). */
    suspend fun resolvedRead(
        format: ConfiguredRecordFormat,
        ref: DataRef,
        fingerprint: DataContentFingerprint?,
        resolve: suspend () -> ResolvedReadSpec
    ): ResolvedReadSpec {
        val key = fingerprint?.let {
            Digest.build {
                addUtf8(format::class.qualifiedName ?: format::class.toString())
                addDigestible(format)
                addUtf8(ref.id)
                addDigestible(it)
            }
        }
        key?.let(reader::cachedRead)?.let { return it }
        val read = resolve()
        key?.let { reader.storeRead(it, read) }
        return read
    }
}
