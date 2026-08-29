package tech.kzen.auto.server.exec.job.bench

import kotlin.test.Test


class JobBenchmarkSmokeTest {
    private val smokeRows = 2_000

    @Test
    fun sliceMatchesInline() {
        JobReportBenchmark.verifySlice(smokeRows)
    }

    @Test
    fun aggregateMatchesReportAndInline() {
        JobReportBenchmark.verifyAggregate(smokeRows)
    }

    @Test
    fun headerlessAggregateCompletes() {
        JobReportBenchmark.verifyHeaderlessAggregate(smokeRows)
    }

    @Test
    fun exportMatchesReport() {
        JobReportBenchmark.verifyExport(smokeRows)
    }
}
