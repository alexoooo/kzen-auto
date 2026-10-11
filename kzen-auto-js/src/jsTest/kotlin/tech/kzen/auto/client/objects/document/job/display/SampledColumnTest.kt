package tech.kzen.auto.client.objects.document.job.display

import tech.kzen.auto.common.objects.document.job.JobReadConventions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class SampledColumnTest {
    @Test
    fun readsEachColumnWithTheFilesThatHoldIt() {
        val details = mapOf(
            JobReadConventions.sampledColumnsKey to listOf(
                column("id", "a.csv", "b.csv"),
                column("extra", "b.csv")),
            JobReadConventions.sampledFilesKey to 2.0)

        val sampled = SampledColumn.of(details)

        assertEquals(
            SampledColumn.Sampled(
                listOf(SampledColumn("id", listOf("a.csv", "b.csv")), SampledColumn("extra", listOf("b.csv"))),
                2),
            sampled)
        assertEquals("In 1 of 2 files: b.csv", sampled?.columns?.last()?.tooltip(2))
    }


    @Test
    fun noColumnsReportedIsNone() {
        assertNull(SampledColumn.of(mapOf()))
    }


    private fun column(name: String, vararg files: String): Map<String, Any?> =
        mapOf(
            JobReadConventions.sampledColumnNameKey to name,
            JobReadConventions.sampledColumnFilesKey to files.toList())
}
