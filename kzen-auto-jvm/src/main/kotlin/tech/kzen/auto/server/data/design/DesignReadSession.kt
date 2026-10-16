package tech.kzen.auto.server.data.design

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.data.api.DataContext
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.objects.datasource.DesignDataContext
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.util.digest.Digest


/**
 * One validation pass's reading of data before Run (docs/plans/2026-09-24_values-metadata-and-design-time-types.md,
 * R5), shared by every Worker of the pass: each [read] reads to the end. [evidence] is what the pass looked at, so
 * its result is reused only while the data is unchanged; what a read inspects is cached by the opener against the
 * content's fingerprint ([tech.kzen.auto.server.data.SchemaCache]), so a pass after an edit reads only what changed.
 *
 * A read blocks the calling thread, as the rest of validation does (its expression compiles).
 */
class DesignReadSession {
    //-----------------------------------------------------------------------------------------------------------------
    private val context: DataContext = DesignDataContext(ExecutionRequest(RequestParams.empty, null))
    private val recorded = mutableListOf<DesignEvidence>()

    val evidence: List<DesignEvidence>
        get() = recorded


    //-----------------------------------------------------------------------------------------------------------------
    fun record(evidence: DesignEvidence) {
        recorded.add(evidence)
    }


    /**
     * A [read] of [block] whose result the pass depends on: records it as evidence under [subject], digested by
     * [digest], whose recheck runs [block] again.
     */
    fun <T> observe(
        subject: String,
        block: suspend (DataContext) -> T,
        digest: (T) -> Digest
    ): T {
        val result = read(block)
        record(DesignEvidence(subject, digest(result)) {
            runBlocking { digest(block(context)) }
        })
        return result
    }


    fun <T> read(block: suspend (DataContext) -> T): T =
        runBlocking {
            block(context)
        }


    //-----------------------------------------------------------------------------------------------------------------
    /** The shape of [part], as its opener inspects it. */
    suspend fun inspectShape(context: DataContext, openerLookup: DataOpenerLookup, part: DataPart): DataShape? =
        openerLookup.openerFor(part.ref).inspectShape(context, part)
}
