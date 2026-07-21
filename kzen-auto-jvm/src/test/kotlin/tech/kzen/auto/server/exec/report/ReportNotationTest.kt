package tech.kzen.auto.server.exec.report

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.objects.report.ReportDocument
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.server.exec.engine.RunEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.fail


/**
 * End-to-end: load a real Report notation, translate it with [ReportLogicCompiler], and run its record pipeline
 * on the [RunEngine] — the Report analogue of [tech.kzen.auto.server.exec.flow.FlowNotationTest] /
 * [tech.kzen.auto.server.exec.job.JobNotationTest]. Reaching [Outcome.Success] proves the whole Report stack
 * runs on the engine: the document's spec → run-context derivation, the engine-driven disruptor pipeline
 * (input parse → formula → table output) driven to completion via [tech.kzen.lib.common.exec.engine.Execution.checkpoint],
 * and the persisted Explore output. The offline output info then confirms the input rows actually flowed through
 * to the materialized table (not just that the run completed).
 */
class ReportNotationTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/report-engine-test.yaml")
    private val reportLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

    // The fixture's input.selection.locations[0].location, resolved (like the Job notation tests) against the
    // test-JVM working directory so the report pipeline and this test reach the same file.
    private val inputCsv = Path.of("build/report-engine-test/input.csv")

    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun reportRunsOnEngineAndProducesExploreOutput() {
        context = KzenAutoContext.forTest()

        Files.createDirectories(inputCsv.parent)
        Files.writeString(
            inputCsv,
            "name,qty,price\n" +
            "apple,3,2\n" +
            "banana,5,1\n" +
            "cherry,2,4\n")

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        // The same run-context the compiler derives (a fresh instance, but signature-keyed to the same run dir),
        // kept for the offline output assertion below.
        val graphInstance = GraphCreator.createGraph(
            graphDefinition.filterTransitive(documentPath), context.graphEnvironment)
        val reportDocument = graphInstance[reportLocation]!!.reference as ReportDocument
        val reportRunContext = assertNotNull(reportDocument.reportRunContext())

        val reportLogic = ReportLogicCompiler.compile(
            reportLocation,
            graphNotation,
            graphDefinition,
            LogicCompilerServices(
                context.graphEnvironment,
                context.objectStableMapper,
                context.cachedKotlinCompiler,
                context.scriptValidationCache,
                context.notationMetadataReader,
                context.jobWorkPool,
                LogicRunExecutionId.random()))

        val engine = RunEngine(reportLogic, context.objectStableMapper.objectStableId(reportLocation))
        val outcome =
            try {
                runBlocking {
                    engine.resume()
                    engine.await()
                }
            }
            finally {
                engine.close()
            }

        if (outcome is Outcome.Failed) {
            fail("Report run failed: ${outcome.message}")
        }
        assertIs<Outcome.Success>(outcome)

        // The 3 input rows flowed through the engine-driven pipeline to the persisted Explore table.
        val outputInfo = ReportRun.outputInfoOffline(reportRunContext, context.reportWorkPool)
        assertEquals(OutputStatus.Done, outputInfo.status)
        assertEquals(3L, assertNotNull(outputInfo.table).rowCount)
    }
}
