package tech.kzen.auto.server.exec.report

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.objects.report.ReportDocument
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.data.binding.BindingSchema
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


/**
 * A Report run as a HOSTED child of a Script rather than as the run root (ledger row 44) — the counterpart to
 * [ReportNotationTest], which runs the same shape top-level.
 *
 * **Every Logic document is hostable, Report included; that is a design point of kzen, not an accident.**
 * `LogicCompiler` has no flavour `when` at all — it resolves `main`'s archetype and dispatches polymorphically
 * through `LogicDocument.toLogic`, so a new paradigm is added as a notation archetype and never by editing the
 * compiler. This fixture exists because two KDoc comments used to assert the opposite ("a Report is always
 * top-level (never hosted)"; "Report excepted: it is top-level only") while **no executable layer anywhere
 * enforced it**: `ReportDocument` implements `LogicDocument`, `ReportLogicCompiler` throws only on a non-Report
 * or a missing input, `RunStep.definition` types any non-Script target as `Any`, and `SelectLogicEditor` has
 * always offered Reports in its dropdown because `AutoConventions.isLogic` matches the `Logic` marker a Report
 * carries. The comments were the defect; they are now corrected, and this test is what keeps them corrected.
 *
 * Compiles through [LogicCompiler] rather than [ReportLogicCompiler] deliberately — the flavour-blind dispatch
 * is precisely what is under test, and reaching the Report through a `RunStep` is what makes it blind.
 *
 * The row-count assertion is load-bearing and not decoration. A hosted Report that silently did nothing would
 * still reach [Outcome.Success], because the host boundary reports the child's outcome and an empty pipeline
 * completes cleanly — so success alone proves only that nothing threw. Reading the materialized table back is
 * what distinguishes "ran" from "returned".
 *
 * A Report deliberately declares and returns no result component. Its observable product is the materialized
 * report read through the inspection/download surfaces; this test pins both the empty signature and the data.
 */
class ReportHostedTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val scriptPath = DocumentPath.parse("test/report/report-hosted-script-test.yaml")
    private val reportPath = DocumentPath.parse("test/report/report-hosted-test.yaml")

    private val scriptLocation = ObjectLocation(scriptPath, ObjectPath.parse("main"))
    private val reportLocation = ObjectLocation(reportPath, ObjectPath.parse("main"))

    // The Report fixture's input.selection.locations[0].location, resolved against the test-JVM working
    // directory so the report pipeline and this test reach the same file (as ReportNotationTest does).
    private val inputCsv = Path.of("build/report-hosted-test/input.csv")

    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun aScriptRunStepHostsAReportAndDrivesItsPipelineToCompletion() {
        context = KzenAutoContext.forTest()

        // Written before anything is compiled: `datasetInfo()` reads real headers off this file, and a null
        // result makes `reportRunContext()` null, which ReportLogicCompiler turns into a hard failure. The
        // hosted case compiles the Report LAZILY, when the RunStep reaches it mid-run, so this only has to
        // precede engine.await() — but there is no reason to cut it that fine.
        Files.createDirectories(inputCsv.parent)
        Files.writeString(
            inputCsv,
            "name,qty,price\n" +
            "apple,3,2\n" +
            "banana,5,1\n" +
            "cherry,2,4\n")

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        // The same run context the compiler will derive (a fresh instance, signature-keyed to the same run
        // dir), kept so the offline output can be read back after the run.
        val graphInstance = GraphCreator.createGraph(
            graphDefinition.filterTransitive(reportPath), context.graphEnvironment)
        val reportDocument = graphInstance[reportLocation]!!.reference as ReportDocument
        val reportRunContext = assertNotNull(reportDocument.reportRunContext())

        val compilerServices = LogicCompilerServices(
            context.graphEnvironment,
            context.objectStableMapper,
            context.cachedKotlinCompiler,
            context.scriptValidationCache,
            context.jobValidationCache,
            context.notationMetadataReader,
            context.jobWorkPool,
            LogicRunExecutionId.random())
        val reportLogic = LogicCompiler.compile(
            reportLocation, graphNotation, graphDefinition, compilerServices)
        assertEquals(BindingSchema.empty, reportLogic.signature().outputs)

        val scriptLogic = LogicCompiler.compile(
            scriptLocation,
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

        val engine = RunEngine(scriptLogic, context.objectStableMapper.objectStableId(scriptLocation))
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

        assertIs<Outcome.Success>(outcome, (outcome as? Outcome.Failed)?.message)

        val outputInfo = ReportRun.outputInfoOffline(reportRunContext, context.reportWorkPool)
        assertEquals(OutputStatus.Done, outputInfo.status)
        assertEquals(
            3L, assertNotNull(outputInfo.table).rowCount,
            "all three input rows flowed through the disruptor pipeline to the materialized table while the " +
                    "Report was running as somebody's child — the assertion that separates 'hosted and ran' " +
                    "from 'hosted and quietly did nothing'")
    }
}
