package tech.kzen.auto.server.objects.report.pipeline.calc

import org.junit.Test
import tech.kzen.auto.server.objects.report.ReportWorkPool
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordItemBuffer
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.service.compile.EmbeddedKotlinCompiler
import tech.kzen.auto.util.WorkUtils
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.assertEquals


@ExperimentalPathApi
class CalculatedColumnEvalTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun twoPlusTwoIsFour() {
        val calculatedColumnEval = calculatedColumnEval()

        val columnNames = listOf("bar", "baz")
        val calculatedColumn = calculatedColumnEval.create(
            "foo",
        "2 + 2",
            columnNames)

        val value = calculatedColumn.evaluate(
            RecordItemBuffer.of("1", "2"),
            RecordHeader.of(columnNames))

        assertEquals("4", value)
    }


    @Test
    fun columnValueSum() {
        val calculatedColumnEval = calculatedColumnEval()

        val columnNames = listOf("bar", "baz")
        val calculatedColumn = calculatedColumnEval.create(
            "foo",
            "bar + baz",
            columnNames)

        val value = calculatedColumn.evaluate(
            RecordItemBuffer.of("2", "3"),
            RecordHeader.of(columnNames))

        assertEquals("5", value)
    }


    private fun calculatedColumnEval(): CalculatedColumnEval {
        val kotlinCompiler = EmbeddedKotlinCompiler()
        val workUtils = WorkUtils.temporary("CachedKotlinCompiler")
        val reportWorkPool = ReportWorkPool(workUtils)
        val cachedKotlinCompiler = CachedKotlinCompiler(kotlinCompiler, reportWorkPool, workUtils)
        return CalculatedColumnEval(cachedKotlinCompiler)
    }
}