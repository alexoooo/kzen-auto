package tech.kzen.auto.client.objects.document.common.valid

import kotlinx.coroutines.delay
import tech.kzen.auto.common.objects.document.logic.ValidationDigestEcho
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.util.digest.Digest


/**
 * Fetches a server-side document validation until its [ValidationDigestEcho] matches the caller's current
 * local host-document digest — the single staleness gate for the fetch-validated flavours (Script, Job).
 *
 * A fetch launched off the LOCAL commit publish can be served from pre-commit server notation (the mirrored
 * store applies local and remote concurrently), and overlapping responses can land out of order; either way
 * the echo mismatches, so the result is retried rather than applied against the code it was not computed for.
 *
 * Convergence: the commit's remote write completes shortly after (apply awaits it), and any newer local edit
 * re-arms a refresh that supersedes this one — signalled by [fetchCurrent]'s currentDigest returning null.
 */
object ServerValidationFetch {
    //-----------------------------------------------------------------------------------------------------------------
    // The remote write the fetch is racing is a local file write, so one retry usually lands; the limit turns a
    // never-converging server (version skew) into a visible error rather than a silently stale display.
    private const val staleRetryDelayMillis = 100L
    private const val staleRetryLimit = 10


    //-----------------------------------------------------------------------------------------------------------------
    sealed interface Outcome<out T> {
        data class Current<T>(val value: T): Outcome<T>
        data class Failed(val errorMessage: String): Outcome<Nothing>
        data object Superseded: Outcome<Nothing>
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @param currentDigest the caller's CURRENT local host-DocumentNotation digest; null means this refresh no
     *  longer owns the outcome (superseded epoch, or the store unmounted) — abort silently.
     * @param perform one detached validator call.
     * @param parse flavour-specific decoding of the validator's value.
     */
    suspend fun <T> fetchCurrent(
        currentDigest: () -> Digest?,
        perform: suspend () -> ExecutionResult,
        parse: (MapExecutionValue) -> T
    ): Outcome<T> {
        repeat(staleRetryLimit) { attempt ->
            if (attempt > 0) {
                delay(staleRetryDelayMillis)
            }

            currentDigest()
                ?: return Outcome.Superseded

            when (val result = perform()) {
                is ExecutionFailure ->
                    return Outcome.Failed(result.errorMessage)

                is ExecutionSuccess -> {
                    // Compared at APPLICATION time (post-response), not launch time; the JS event loop leaves no
                    // interleave between this check and the caller's state write.
                    val localDigest = currentDigest()
                        ?: return Outcome.Superseded

                    val echoedDigest = ValidationDigestEcho.ofDetail(result.detail)
                    if (echoedDigest == null || echoedDigest == localDigest) {
                        return Outcome.Current(parse(result.value as MapExecutionValue))
                    }
                }
            }
        }

        return Outcome.Failed("Validation did not converge with the edited document")
    }
}
