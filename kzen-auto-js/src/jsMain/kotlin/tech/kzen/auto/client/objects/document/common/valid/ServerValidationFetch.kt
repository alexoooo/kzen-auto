package tech.kzen.auto.client.objects.document.common.valid

import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.MapExecutionValue


/**
 * Fetches a server-side document validation (Script, Job) and drops an answer a newer refresh has superseded.
 *
 * The server always answers from notation that includes every edit already sent, because `ClientRestApi` holds the
 * request until those writes land (NotationWriteBarrier). What remains is ordering among overlapping refreshes:
 * an older answer that arrives after a newer refresh was armed is discarded rather than applied.
 */
object ServerValidationFetch {
    //-----------------------------------------------------------------------------------------------------------------
    sealed interface Outcome<out T> {
        data class Current<T>(val value: T): Outcome<T>
        data class Failed(val errorMessage: String): Outcome<Nothing>
        data object Superseded: Outcome<Nothing>
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * @param isCurrent false once this refresh no longer owns the outcome (a newer one was armed, or the store
     *  unmounted) — the result is then dropped silently.
     * @param perform one detached validator call.
     * @param parse flavour-specific decoding of the validator's value.
     */
    suspend fun <T> fetch(
        isCurrent: () -> Boolean,
        perform: suspend () -> ExecutionResult,
        parse: (MapExecutionValue) -> T
    ): Outcome<T> {
        if (!isCurrent()) {
            return Outcome.Superseded
        }

        val result = perform()

        // Checked when the answer arrives; the JS event loop leaves no interleave between this check and the
        // caller's state write.
        if (!isCurrent()) {
            return Outcome.Superseded
        }

        return when (result) {
            is ExecutionFailure -> Outcome.Failed(result.errorMessage)
            is ExecutionSuccess -> Outcome.Current(parse(result.value as MapExecutionValue))
        }
    }
}
