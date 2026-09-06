package tech.kzen.auto.server.exec.job.ownership

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.job.JobLogicCompiler
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * E8 over E9 (HS19 Work 4): owned orders projected to `symbol, executions[*].price, executions[*].qty` — the
 * rows are detached scalar copies that stay valid after the run closed every order, and their aggregate equals
 * a direct fold over the same objects.
 */
class PathProjectionRouteTest {
    private val runTimeoutMillis = 30_000L

    private lateinit var context: KzenAutoContext


    @BeforeTest
    fun reset() {
        OwnedSourceWorker.reset()
        ObservingSinkWorker.reset()
    }

    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    @Test
    fun projectedRowsOutliveTheClosedOrdersAndFoldToTheSameTotal() {
        OwnedSourceWorker.kind = OwnedSourceWorker.kindOrder
        OwnedSourceWorker.names = listOf("AAPL", "MSFT", "NVDA")
        assertIs<Outcome.Success>(run())

        val orders = OwnedSourceWorker.orders
        assertEquals(3, orders.size)
        assertTrue(orders.all { it.closes == 1 }, "every order closed once, after its projection: $orders")

        val rows = ObservingSinkWorker.of("sink").map { it.value as Map<*, *> }
        assertEquals(orders.sumOf { it.executions.size }, rows.size, "one row per execution")
        assertEquals(
            orders.flatMap { order -> order.executions.map { order.symbol } },
            rows.map { it["symbol"] })
        val projectedNotional = rows.sumOf { (it["executions.price"] as Double) * (it["executions.qty"] as Long) }
        assertEquals(orders.sumOf { it.notional() }, projectedNotional, 1e-9)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun run(): Outcome {
        context = KzenAutoContext.forTest()
        val jobLocation = ObjectLocation(
            DocumentPath.parse("test/job/ownership/owned-projection-test.yaml"), ObjectPath.parse("main"))
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful
        val jobLogic = JobLogicCompiler.compile(
            jobLocation,
            graphNotation,
            graphDefinition,
            LogicCompilerServices(
                context.graphEnvironment,
                context.objectStableMapper,
                context.cachedKotlinCompiler,
                context.scriptValidationCache,
                context.jobValidationCache,
                context.notationMetadataReader,
                context.jobWorkPool,
                LogicRunExecutionId.random()))
        val engine = RunEngine(jobLogic, context.objectStableMapper.objectStableId(jobLocation))
        return try {
            runBlocking {
                withTimeout(runTimeoutMillis) {
                    engine.resume()
                    engine.await()
                }
            }
        }
        finally {
            engine.close()
        }
    }
}
