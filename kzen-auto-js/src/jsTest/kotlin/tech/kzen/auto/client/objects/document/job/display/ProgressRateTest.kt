package tech.kzen.auto.client.objects.document.job.display

import tech.kzen.auto.client.objects.document.job.JobWorkerProgress
import tech.kzen.auto.client.objects.document.job.WorkerOutcome
import tech.kzen.auto.common.objects.document.job.JobConventions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class ProgressRateTest {
    @Test
    fun theRateIsOverTheRecentWindowWhileRunning() {
        val rate = ProgressRate(windowMillis = 2_000)
        assertNull(rate.perSecond(at(1_000), 100))
        assertEquals(400.0, rate.perSecond(at(2_000), 500))
        assertEquals(400.0, rate.perSecond(at(2_000), 500), "the same sample again changes nothing")
        assertEquals(450.0, rate.perSecond(at(3_000), 1_000))

        // Faster lately: the first sample has left the window, so the rate follows
        assertEquals(1_250.0, rate.perSecond(at(4_000), 3_000))
    }


    @Test
    fun aCountThatGoesBackStartsAgain() {
        val rate = ProgressRate()
        rate.perSecond(at(1_000), 900)
        assertNull(rate.perSecond(at(2_000), 100), "the next file's count starts again")
        assertEquals(200.0, rate.perSecond(at(3_000), 300))
    }


    @Test
    fun onceDoneItIsTheAverageOverTheRun() {
        val rate = ProgressRate()
        rate.perSecond(at(1_000), 100)
        val done = JobWorkerProgress(
            null,
            mapOf(JobConventions.progressElapsedKey to 4_000.0),
            WorkerOutcome(WorkerOutcome.Kind.Success, null))
        assertEquals(500.0, rate.perSecond(done, 2_000))
    }


    @Test
    fun withoutTheClockThereIsNoRate() {
        assertNull(ProgressRate().perSecond(JobWorkerProgress(null, mapOf()), 100))
        assertNull(ProgressRate().perSecond(null, 100))
    }


    @Test
    fun ratesReadAsSizesOrCounts() {
        assertEquals("1.5 MB/s", ProgressRate.bytesText(1_572_864.0))
        assertEquals("12,346 rows/s", ProgressRate.countText(12_345.6, "rows"))
    }


    private fun at(elapsedMillis: Long): JobWorkerProgress =
        JobWorkerProgress(null, mapOf(JobConventions.progressElapsedKey to elapsedMillis))
}
