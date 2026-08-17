package tech.kzen.auto.common.objects.document.target.model


/**
 * Lifecycle of one of the target editor's fetch channels. [TargetFetchPlan] schedules off phases alone, so what
 * to fetch next is decidable — and testable — without any of the payloads.
 *
 * [Failed] is terminal in the same sense [Loaded] is: a channel that failed is not retried until something
 * re-arms it to [Idle], which is what keeps a failing endpoint from being asked again on every publish.
 */
enum class TargetFetchPhase {
    Idle,
    Requesting,
    Loaded,
    Failed
}
