package tech.kzen.auto.server.objects.script.api

import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * A structured control-flow transfer within one Script document — continue / break / return — modelled as a
 * COMPLETION SIGNAL rather than an exception. A step raises one via [StepExecution.raiseControlSignal]; the
 * framework spine ([StepExecution.runSteps]) short-circuits the remaining steps and the targeted consumer clears
 * it (a loop for [SkipIteration] / [FinishLoop]; the Script root for [EndScript]).
 *
 * Why a signal and not an exception: the engine's `Execution.recoverable {}` catch-all would render an unwinding
 * control throwable as a step failure (error-parked under pause-on-error). A signal keeps the engine contract
 * untouched — the whole feature is flavour-level, with zero kzen-lib change. A signal is raised and consumed
 * within ONE engine release: it never survives a `checkpoint`/park, never migrates, and never crosses a
 * `host()` boundary (a hosted child runs in its own context with its own pending signal), so [EndScript] in a
 * sub-Script ends only the sub-Script.
 *
 * Targets are [ObjectLocation]s; a loop compares them in stable-id space (rename-safe), though a rename cannot
 * occur between raise and consume anyway since a signal never survives a checkpoint.
 */
sealed class ScriptControlSignal {
    /** `continue`: skip the rest of the target loop's current iteration and proceed to the next. */
    data class SkipIteration(val target: ObjectLocation): ScriptControlSignal()

    /** `break`: exit the target loop immediately (it returns the outputs collected so far). */
    data class FinishLoop(val target: ObjectLocation): ScriptControlSignal()

    /** `return`: end the current Script document, returning its captured result. */
    object EndScript: ScriptControlSignal()
}
