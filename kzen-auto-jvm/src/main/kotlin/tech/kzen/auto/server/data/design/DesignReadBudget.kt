package tech.kzen.auto.server.data.design

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


/**
 * How much one validation pass may read before Run (docs/plans/2026-09-24_values-metadata-and-design-time-types.md,
 * R5): at most [maxValues] values of any one lane, within [deadline] for the whole pass. The value limit is a fixed
 * bound: a longer lane is typed from its first values and says so ("Inferred from 256 of 900 values"). A pass the
 * deadline cuts short is partial, so the next pass reads on from the cached inspections.
 */
data class DesignReadBudget(
    val maxValues: Int,
    val deadline: Duration
) {
    companion object {
        /** The editor's pass: short enough never to hold up editing. */
        val editor = DesignReadBudget(256, 2500.milliseconds)

        /** A run's revalidation, when the data changed since the editor's pass: the run waits for it. */
        val run = DesignReadBudget(256, 30.seconds)
    }

    init {
        require(maxValues > 0) { "Design-time value limit must be positive: $maxValues" }
    }
}
