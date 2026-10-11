package tech.kzen.auto.client.objects.document.job.display

import tech.kzen.auto.client.objects.document.job.JobWorkerProgress
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.util.FormatUtils
import kotlin.math.roundToLong


/**
 * How fast one count of a Worker's progress grows, per second of the run's clock (the elapsed time the server
 * stamps on every progress map, [JobConventions.progressElapsedKey]). While the Worker runs it is the rate over
 * the last [windowMillis] of samples, so it follows the speed now rather than since the start; once the Worker
 * has an outcome it is the average over its run. A count that goes back (a new run, or a per-file count moving to
 * the next file) starts the samples again.
 *
 * Sampling is idempotent per elapsed time, so a card can take a sample whenever it renders.
 */
class ProgressRate(
    private val windowMillis: Long = 5_000
) {
    companion object {
        fun bytesText(perSecond: Double): String =
            "${FormatUtils.readableFileSize(perSecond.roundToLong())}/s"


        fun countText(perSecond: Double, noun: String): String =
            "${FormatUtils.decimalSeparator(perSecond.roundToLong())} $noun/s"
    }


    private class Sample(
        val elapsedMillis: Long,
        val value: Long
    )


    private val samples = ArrayDeque<Sample>()


    /** Per second, or null until there is something to divide (no clock, nothing counted, no time passed). */
    fun perSecond(progress: JobWorkerProgress?, value: Long?): Double? {
        val elapsed = progress?.longValue(JobConventions.progressElapsedKey)
        if (elapsed == null || value == null) {
            return null
        }
        sample(elapsed, value)

        if (progress.outcome != null) {
            return if (elapsed > 0) value * 1000.0 / elapsed else null
        }
        val first = samples.first()
        val last = samples.last()
        val span = last.elapsedMillis - first.elapsedMillis
        if (span <= 0) {
            return null
        }
        return (last.value - first.value) * 1000.0 / span
    }


    private fun sample(elapsedMillis: Long, value: Long) {
        val last = samples.lastOrNull()
        if (last != null && (elapsedMillis < last.elapsedMillis || value < last.value)) {
            samples.clear()
        }
        else if (last != null && elapsedMillis == last.elapsedMillis) {
            return
        }
        samples.addLast(Sample(elapsedMillis, value))
        while (samples.size > 2 && elapsedMillis - samples[1].elapsedMillis >= windowMillis) {
            samples.removeFirst()
        }
    }
}
