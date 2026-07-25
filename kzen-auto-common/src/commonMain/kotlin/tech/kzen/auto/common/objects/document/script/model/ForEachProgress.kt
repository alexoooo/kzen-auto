@file:Suppress("ConstPropertyName")

package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.auto.common.util.TraceDisplay
import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue


/**
 * A ForEach loop's live iteration progress, carried as the loop step's [StepTrace] detail: which item it is on,
 * how far through it is, and the values its body has produced so far.
 *
 * The value journal ([produced]) is display strings only, and is kept regardless of whether the loop is
 * COLLECTING its real values (`StepExecution.isValueReferenced` — see ForEachStep) — a loop nothing references
 * still shows what it computed, without the collected list that would pin every iteration's object for the run.
 * That also makes this the one thing to read after a jump commits a loop's partial value: the committed list and
 * this journal are built at the same point in the iteration, so their entries line up index-for-index.
 *
 * BOUNDED (logic-spec §7): the whole thing ships on every client poll of the live trace, so it caps both the
 * number of retained entries ([maxProducedEntries], keeping the most recent) and each entry's length
 * ([maxProducedEntryChars]). [producedCount] is the untruncated total, so a reader can tell how much was elided.
 */
data class ForEachProgress(
    val item: String,
    val index: Int,
    val size: Int?,
    val produced: List<Entry>,
    val producedCount: Int,

    // Committed mid-flight by a forward move-to (Set Next Statement) that skipped over the loop: the value the
    // loop handed downstream is these iterations only, not the whole collection.
    val partial: Boolean = false
) {
    //-----------------------------------------------------------------------------------------------------------------
    /** One completed iteration: the element it ran on, and the value its body terminal produced. */
    data class Entry(
        val item: String,
        val value: String
    )


    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        /** How many of the most recent iterations the journal retains. */
        const val maxProducedEntries = 100

        /** The cap on each journal entry's item / value display (well under a step display's own cap). */
        const val maxProducedEntryChars = 120


        private const val itemKey = "item"
        private const val indexKey = "index"
        private const val sizeKey = "size"
        private const val producedKey = "produced"
        private const val producedCountKey = "producedCount"
        private const val partialKey = "partial"

        private const val entryItemKey = "item"
        private const val entryValueKey = "value"


        /** Build a capped [Entry] from the raw iteration values (the only sanctioned constructor). */
        fun entryOf(item: Any?, value: Any?): Entry {
            return Entry(
                TraceDisplay.truncatedToString(item, maxProducedEntryChars),
                TraceDisplay.truncatedToString(value, maxProducedEntryChars))
        }


        /**
         * Read a ForEach progress detail, or null when [executionValue] is not one — a step's detail is a
         * free-form [ExecutionValue] (a screenshot, a text note, nothing at all), so every read is a probe.
         */
        fun ofExecutionValueOrNull(executionValue: ExecutionValue?): ForEachProgress? {
            if (executionValue !is MapExecutionValue) {
                return null
            }

            val item = (executionValue.values[itemKey] as? TextExecutionValue)?.value
                ?: return null
            val index = (executionValue.values[indexKey] as? LongExecutionValue)?.value?.toInt()
                ?: return null
            val producedCount = (executionValue.values[producedCountKey] as? LongExecutionValue)?.value?.toInt()
                ?: return null

            val size = (executionValue.values[sizeKey] as? LongExecutionValue)?.value?.toInt()

            val produced = (executionValue.values[producedKey] as? ListExecutionValue)
                ?.values
                ?.mapNotNull { entryOfExecutionValueOrNull(it) }
                ?: listOf()

            val partial = (executionValue.values[partialKey] as? BooleanExecutionValue)?.value == true

            return ForEachProgress(item, index, size, produced, producedCount, partial)
        }


        private fun entryOfExecutionValueOrNull(executionValue: ExecutionValue): Entry? {
            if (executionValue !is MapExecutionValue) {
                return null
            }
            val item = (executionValue.values[entryItemKey] as? TextExecutionValue)?.value
                ?: return null
            val value = (executionValue.values[entryValueKey] as? TextExecutionValue)?.value
                ?: return null
            return Entry(item, value)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * NB: the counts go over the wire as [LongExecutionValue] (serialized as a String), not via
     * `ExecutionValue.of(Int)` — kzen-lib has no Int scalar, so an Int would become a NumberExecutionValue
     * (a Double) that renders "3" on JS and "3.0" on JVM.
     */
    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(mapOf(
            itemKey to TextExecutionValue(item),
            indexKey to LongExecutionValue(index.toLong()),
            sizeKey to (size?.let { LongExecutionValue(it.toLong()) } ?: NullExecutionValue),
            producedKey to ListExecutionValue(produced.map { it.asExecutionValue() }),
            producedCountKey to LongExecutionValue(producedCount.toLong()),
            partialKey to BooleanExecutionValue.of(partial)
        ))
    }


    private fun Entry.asExecutionValue(): ExecutionValue {
        return MapExecutionValue(mapOf(
            entryItemKey to TextExecutionValue(item),
            entryValueKey to TextExecutionValue(value)
        ))
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** How many journal entries were dropped by [maxProducedEntries] — zero when the journal is complete. */
    fun omittedCount(): Int {
        return producedCount - produced.size
    }
}
