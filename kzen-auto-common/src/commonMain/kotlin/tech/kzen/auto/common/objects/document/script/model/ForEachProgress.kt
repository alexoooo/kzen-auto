@file:Suppress("ConstPropertyName")

package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue


/**
 * A ForEach loop's live iteration progress, carried as the loop step's [StepTrace] detail: which item it is on,
 * how far through it is, and how many values its body has produced so far.
 *
 * The values themselves are the loop's journal, one [Entry] per completed iteration, traced as a list beside this
 * detail (`StepExecution.traceList`) and read by range (`LogicTrace.lookupRange`), so this detail is the same size
 * at every iteration. The journal is kept regardless of whether the loop is COLLECTING its real values
 * (`StepExecution.isValueReferenced` — see ForEachStep) — a loop nothing references still shows what it computed,
 * without the collected list that would pin every iteration's object for the run. That also makes it the one
 * thing to read after a jump commits a loop's partial value: the committed list and the journal are built at the
 * same point in the iteration, so their entries line up index-for-index.
 */
data class ForEachProgress(
    val item: String,
    val index: Int,
    val size: Int?,
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
    ) {
        fun asExecutionValue(): ExecutionValue {
            return MapExecutionValue(mapOf(
                entryItemKey to TextExecutionValue(item),
                entryValueKey to TextExecutionValue(value)
            ))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val itemKey = "item"
        private const val indexKey = "index"
        private const val sizeKey = "size"
        private const val producedCountKey = "producedCount"
        private const val partialKey = "partial"

        private const val entryItemKey = "item"
        private const val entryValueKey = "value"


        /** Build an [Entry] from the raw iteration values (the only sanctioned constructor). */
        fun entryOf(item: Any?, value: Any?): Entry {
            return Entry(item.toString(), value.toString())
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

            val partial = (executionValue.values[partialKey] as? BooleanExecutionValue)?.value == true

            return ForEachProgress(item, index, size, producedCount, partial)
        }


        /** Read one journal entry, as traced in the loop's list, or null when [executionValue] is not one. */
        fun entryOfExecutionValueOrNull(executionValue: ExecutionValue): Entry? {
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
            producedCountKey to LongExecutionValue(producedCount.toLong()),
            partialKey to BooleanExecutionValue.of(partial)
        ))
    }
}
