package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.FormulaCarrySpec
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import java.math.BigDecimal
import kotlin.test.assertEquals


class DynamicExpressionWorkerTest {
    private lateinit var context: KzenAutoContext


    @Before
    fun setUp() {
        context = KzenAutoContext.forTest()
    }


    @After
    fun tearDown() {
        context.close()
    }


    @Test
    fun filterRetainsExplicitKeyAccessWhenDynamicLaneResolvesToMapping() = runBlocking {
        val output = CapturingOutput()
        val worker = FilterWorker(
            SingleInput(listOf(
                mapping("1.5"),
                mapping("3.5"))),
            output,
            "(key(\"amount\") as BigDecimal) > BigDecimal(\"2\")",
            location("filter"),
            context.jobExpressionCompiler)

        worker.run(DynamicInputControl)

        assertEquals(
            listOf(linkedMapOf("amount" to BigDecimal("3.5"))),
            output.values.map(::testBoundary))
    }


    @Test
    fun formulaRetainsExplicitKeyAccessWhenDynamicLaneResolvesToMapping() = runBlocking {
        val output = CapturingOutput()
        val worker = FormulaWorker(
            SingleInput(listOf(mapping("2.5"))),
            output,
            FormulaSpec(mapOf(
                "doubled" to "(key(\"amount\") as BigDecimal) * BigDecimal(\"2\")")),
            "",
            FormulaCarrySpec.none,
            location("formula"),
            context.jobExpressionCompiler)

        worker.run(DynamicInputControl)

        val projection = testProjection(output.values.single())
        assertEquals(listOf("amount", "doubled"),
            (0 until projection.size).map { projection.field(it).name })
        assertEquals(listOf("2.5", "5"),
            (0 until projection.size).map(projection::render))
    }


    private fun mapping(amount: String): DataValue =
        JobDataValues.lift(linkedMapOf("amount" to BigDecimal(amount)))


    private fun location(worker: String): ObjectLocation =
        ObjectLocation.parse("test/dynamic-expression-worker-test.yaml#main.workers/$worker")


    private class SingleInput(private val values: List<DataValue>): ChannelInput<Any?> {
        private var delivered = false

        override suspend fun receiveBatch(): List<Any?>? {
            if (delivered) return null
            delivered = true
            return values
        }

        override suspend fun receive(): Any? = error("unused")
        override fun iterator(): ChannelInputIterator<Any?> = error("unused")
    }


    private class CapturingOutput: ChannelOutput<DataValue> {
        val values = mutableListOf<DataValue>()

        override suspend fun send(element: DataValue) {
            values += element
        }

        override suspend fun flush() {}
        override fun batchSize(): Int = 16
        override fun close() {}
    }


    private object DynamicInputControl: JobControl {
        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String = error("unused")
        override fun publishProgress(
            location: ObjectLocation,
            value: Map<String, Any?>,
            force: Boolean
        ) {}
        override fun inputContract(): DataContract = DataContract(DataType.Dynamic())
        override suspend fun host(instructions: ObjectLocation, input: Any?) = error("unused")
    }
}
