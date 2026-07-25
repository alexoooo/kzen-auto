package tech.kzen.auto.server.objects.script.api

import tech.kzen.lib.common.exec.ExecutionValue


/**
 * What a mid-flight step hands over when a forward move-to (Set Next Statement) skips past it — see
 * [ScriptStep.partialOutcome].
 *
 * [detail] rather than a plain value because the skipped step is adopted, not run: the spine's replay
 * short-circuit re-emits its trace, which would otherwise blank the display the step had built up while it was
 * running (a loop's iteration journal). Carrying the detail with the value keeps the card explaining itself.
 */
class PartialOutcome(
    val value: Any?,
    val detail: ExecutionValue
)
