package tech.kzen.auto.client.objects.document.job.display

import tech.kzen.auto.common.objects.document.job.JobReadConventions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class InferredColumnTest {
    @Test
    fun readsEachColumnWithTheFilesThatHoldIt() {
        val details = mapOf(
            JobReadConventions.columnsKey to listOf(
                column("id", "a.csv", "b.csv"),
                column("extra", "b.csv")),
            JobReadConventions.inspectedFilesKey to 2.0)

        val inferred = InferredColumn.of(details)

        assertEquals(
            InferredColumn.Inferred(
                listOf(InferredColumn("id", listOf("a.csv", "b.csv")), InferredColumn("extra", listOf("b.csv"))),
                2),
            inferred)
        assertEquals("In 1 of 2 files: b.csv", inferred?.columns?.last()?.tooltip(2))
    }


    @Test
    fun noColumnsReportedIsNone() {
        assertNull(InferredColumn.of(mapOf()))
    }


    private fun column(name: String, vararg files: String): Map<String, Any?> =
        mapOf(
            JobReadConventions.columnNameKey to name,
            JobReadConventions.columnFilesKey to files.toList())
}
