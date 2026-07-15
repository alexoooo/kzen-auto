package tech.kzen.auto.common.util


/**
 * Bounds a value's human-facing trace rendering.
 *
 * A trace display is for a person to read, so it is capped independently of the value it describes: the value
 * graph a run computes over (a step's outcome, a vertex's message) is never truncated — downstream expressions
 * and results always see the whole thing. Only the string that reaches the trace, and therefore the wire and the
 * client, is bounded. Without this an unremarkable step (a big list, a file's contents) puts megabytes into
 * every poll.
 *
 * The cap is the caller's choice rather than one global constant, because what counts as "readable" differs per
 * flavour — a Script step's display shows more than a Flow vertex's message chip.
 */
object TraceDisplay {
    /** The cap on a Script step's display value ([maxFlowTraceChars]'s Script counterpart). */
    const val maxScriptTraceChars = 2048


    /** The cap on a Flow vertex's traced message / state. */
    const val maxFlowTraceChars = 1024


    /**
     * [value]'s `toString()`, capped at [maxChars] with a suffix naming how much was elided — so a reader can
     * tell a truncated display from a value that genuinely ends there.
     */
    fun truncatedToString(value: Any?, maxChars: Int): String {
        val asString = value.toString()
        if (asString.length <= maxChars) {
            return asString
        }
        val remaining = asString.length - maxChars
        return asString.take(maxChars) + "… ($remaining more chars)"
    }
}
