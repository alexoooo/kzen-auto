package tech.kzen.auto.server.objects.report.calc

import org.junit.Test
import tech.kzen.auto.server.objects.report.input.model.RecordHeader
import tech.kzen.auto.server.objects.report.input.model.RecordLineBuffer
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.service.compile.EmbeddedKotlinCompiler
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.assertEquals


@ExperimentalPathApi
class CalculatedColumnEvalTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun twoPlusTwoIsFour() {
        val kotlinCompiler = EmbeddedKotlinCompiler()
        val workDir = kotlin.io.path.createTempDirectory("CachedKotlinCompiler")
        val cachedKotlinCompiler = CachedKotlinCompiler(kotlinCompiler, workDir)
        val calculatedColumnEval = CalculatedColumnEval(cachedKotlinCompiler)

        val columnNames = listOf("bar", "baz")
        val calculatedColumn = calculatedColumnEval.eval(
            "foo",
        "2 + 2",
            columnNames)

        val value = calculatedColumn.evaluate(
            RecordLineBuffer.of(listOf("1", "2")),
            RecordHeader.of(columnNames))

        assertEquals("4", value)
    }
}