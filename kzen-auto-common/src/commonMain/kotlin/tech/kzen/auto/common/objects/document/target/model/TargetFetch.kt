package tech.kzen.auto.common.objects.document.target.model


/**
 * One of the target editor's fetch channels: which [TargetFetchPhase] it stands in and, in the two terminal
 * phases, the value or the error it settled on.
 *
 * A channel holds exactly one of those, so "loaded and failed at once", "requesting while still holding the
 * previous value" and "idle with an error" cannot be written down — the states the schedule reasons about are
 * exactly the states that exist.
 */
sealed interface TargetFetch<out T> {
    //-----------------------------------------------------------------------------------------------------------------
    val phase: TargetFetchPhase


    @Suppress("UNCHECKED_CAST")
    val valueOrNull: T?
        get() = (this as? Loaded<T>)?.value


    val errorMessageOrNull: String?
        get() = (this as? Failed)?.errorMessage


    //-----------------------------------------------------------------------------------------------------------------
    data object Idle: TargetFetch<Nothing> {
        override val phase = TargetFetchPhase.Idle
    }


    data object Requesting: TargetFetch<Nothing> {
        override val phase = TargetFetchPhase.Requesting
    }


    data class Loaded<out T>(val value: T): TargetFetch<T> {
        override val phase = TargetFetchPhase.Loaded
    }


    data class Failed(val errorMessage: String): TargetFetch<Nothing> {
        override val phase = TargetFetchPhase.Failed
    }
}
