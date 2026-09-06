package tech.kzen.auto.server.objects.job.worker

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.path.PathProjectionEntry
import tech.kzen.auto.common.objects.document.job.path.PathProjectionSpec
import tech.kzen.auto.common.objects.document.job.path.ProjectionPath
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * The E8 runtime rules on lifted Kotlin objects: shared versus independent wildcards, nested recursion,
 * null versus empty, map key / value in entries order, aliases and collisions, non-scalar rejection, and the
 * static output contract.
 */
class PathProjectionWorkerTest {
    data class Trade(val venue: String)
    data class Execution(val price: Double, val qty: Long, val trade: Trade?)
    data class Order(
        val symbol: String,
        val executions: List<Execution>,
        val tags: Map<String, Long>,
        val notes: List<String>,
        val legs: List<Order>?
    )

    private val filled = Order(
        "AAPL",
        listOf(Execution(10.0, 5, Trade("XNAS")), Execution(11.0, 7, null)),
        linkedMapOf("lot" to 1L, "urgency" to 3L),
        listOf("n1", "n2", "n3"),
        legs = listOf(
            Order("LEG1", listOf(Execution(1.0, 1, Trade("ARCA"))), emptyMap(), emptyList(), null),
            Order("LEG2", emptyList(), emptyMap(), emptyList(), null)))


    @Test
    fun sharedWildcardsIterateTheSameListTogether() {
        val rows = project(filled, "symbol", "executions[*].price", "executions[*].qty")
        assertEquals(
            listOf(
                mapOf("symbol" to "AAPL", "executions.price" to 10.0, "executions.qty" to 5L),
                mapOf("symbol" to "AAPL", "executions.price" to 11.0, "executions.qty" to 7L)),
            rows)
    }


    @Test
    fun independentWildcardsFormTheCrossProduct() {
        val rows = project(filled, "executions[*].qty", "notes[*]")
        assertEquals(6, rows.size)
        assertEquals(listOf(5L, 5L, 5L, 7L, 7L, 7L), rows.map { it["executions.qty"] })
        assertEquals(listOf("n1", "n2", "n3", "n1", "n2", "n3"), rows.map { it["notes"] })
    }


    @Test
    fun nestedWildcardsIterateWithinTheParentAndAnEmptyInnerListYieldsNoRowsForThatParent() {
        val rows = project(filled, "legs[*].symbol", "legs[*].executions[*].price")
        assertEquals(listOf(mapOf("legs.symbol" to "LEG1", "legs.executions.price" to 1.0)), rows)
    }


    @Test
    fun nullIntermediateKeepsTheRowWithNulls() {
        val rows = project(filled, "executions[*].price", "executions[*].trade.venue")
        assertEquals(listOf("XNAS", null), rows.map { it["executions.trade.venue"] })
        assertEquals(2, rows.size)
    }


    @Test
    fun nullOrEmptyUnnestedListYieldsZeroRows() {
        val noLegs = filled.copy(legs = null)
        assertEquals(emptyList(), project(noLegs, "symbol", "legs[*].symbol"))
        val noExecutions = filled.copy(executions = emptyList())
        assertEquals(emptyList(), project(noExecutions, "symbol", "executions[*].price"))
        // Only the unnested path decides: without it the element still projects
        assertEquals(listOf(mapOf("symbol" to "AAPL")), project(noExecutions, "symbol"))
    }


    @Test
    fun mapWildcardExposesKeyAndValueInEntriesOrder() {
        val rows = project(filled, "symbol", "tags[*].key", "tags[*].value")
        assertEquals(
            listOf(
                mapOf("symbol" to "AAPL", "tags.key" to "lot", "tags.value" to 1L),
                mapOf("symbol" to "AAPL", "tags.key" to "urgency", "tags.value" to 3L)),
            rows)
    }


    @Test
    fun aliasesRenameAndCollisionsFailBeforeRunning() {
        val rows = projectWith(filled, PathProjectionSpec(listOf(
            PathProjectionEntry(ProjectionPath.parse("executions[*].price"), "px"),
            PathProjectionEntry(ProjectionPath.parse("symbol")))))
        assertEquals(listOf(10.0, 11.0), rows.map { it["px"] })

        val colliding = PathProjectionSpec(listOf(
            PathProjectionEntry(ProjectionPath.parse("executions[*].price")),
            PathProjectionEntry(ProjectionPath.parse("symbol"), "executions.price")))
        val attempt = worker(colliding).payloadFlow(
            JobLaneDescriptor(JobDataValues.lift(filled).contract), laneContext())
        assertTrue(attempt.errorMessage!!.contains("collides"), attempt.errorMessage)
        val failure = assertFailsWith<IllegalStateException> { projectWith(filled, colliding) }
        assertTrue(failure.message!!.contains("collides"), failure.message)
    }


    @Test
    fun nonScalarLeafIsRejectedPointingAtFormula() {
        val attempt = worker(spec("executions[*].trade")).payloadFlow(
            JobLaneDescriptor(JobDataValues.lift(filled).contract), laneContext())
        assertTrue(attempt.errorMessage!!.contains("Formula"), attempt.errorMessage)
    }


    @Test
    fun staticContractIsTheFlatRecordOfNullableScalarLeaves() {
        val attempt = worker(spec("symbol", "executions[*].price", "tags[*].value")).payloadFlow(
            JobLaneDescriptor(JobDataValues.lift(filled).contract), laneContext())
        assertNull(attempt.errorMessage)
        val record = assertIs<DataType.Record>(attempt.lane.contract.structural)
        assertEquals(listOf("symbol", "executions.price", "tags.value"), record.fields.map { it.id.name })
        assertEquals(
            listOf(ScalarKind.Text, ScalarKind.Floating(64), ScalarKind.Integer(64)),
            record.fields.map { assertIs<DataType.Scalar>(it.type).kind })
        assertTrue(record.fields.all { (it.type as DataType.Scalar).nullable })
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun project(element: Any, vararg paths: String): List<Map<*, *>> =
        projectWith(element, spec(*paths))


    private fun projectWith(element: Any, spec: PathProjectionSpec): List<Map<*, *>> {
        val output = CapturingOutput()
        val worker = PathProjectionWorker(SingleInput(listOf(JobDataValues.lift(element))), output, spec, location())
        runBlocking { worker.run(NoOpControl) }
        return output.values.map { JobDataValues.boundary(it) as Map<*, *> }
    }


    private fun worker(spec: PathProjectionSpec): PathProjectionWorker =
        PathProjectionWorker(SingleInput(emptyList()), CapturingOutput(), spec, location())


    private fun spec(vararg paths: String): PathProjectionSpec =
        PathProjectionSpec(paths.map { PathProjectionEntry(ProjectionPath.parse(it)) })


    private fun location(): ObjectLocation =
        ObjectLocation.parse("test/path-projection-unit-test.yaml#main.workers/paths")


    private fun laneContext(): JobLaneContext =
        JobLaneContext(BindingSchema.empty, GraphStructure.empty, PathProjectionWorker::class.java.classLoader)


    private class SingleInput(private val values: List<DataValue>): ChannelInput<Any?> {
        private var delivered = false

        override suspend fun receiveBatch(): List<Any?>? {
            if (delivered || values.isEmpty()) return null
            delivered = true
            return values
        }

        override suspend fun receive(): Any? = error("unused")
        override fun iterator(): ChannelInputIterator<Any?> = error("unused")
    }


    private class CapturingOutput: ChannelOutput<DataValue> {
        val values = mutableListOf<DataValue>()

        override suspend fun send(element: DataValue) {
            values.add(element)
        }

        override suspend fun flush() {}
        override fun batchSize(): Int = 16
        override fun close() {}
    }


    private object NoOpControl: JobControl {
        override suspend fun checkpoint() {}
        override suspend fun <R> runBlockingIo(block: () -> R): R = block()
        override fun scratchDir(): String = error("unused")
        override fun publishProgress(location: ObjectLocation, value: Map<String, Any?>, force: Boolean) {}
        override suspend fun host(instructions: ObjectLocation, input: Any?) = error("unused")
    }
}
