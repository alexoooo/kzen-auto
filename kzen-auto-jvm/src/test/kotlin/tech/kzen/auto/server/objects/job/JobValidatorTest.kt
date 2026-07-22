package tech.kzen.auto.server.objects.job

import tech.kzen.auto.common.objects.document.job.JobChannelSynthesis
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.service.context.GraphCreator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


/**
 * Unit test for [JobValidator.validate] — the static payload-type walk: lanes fold along the order-driven
 * wiring, each Worker's [tech.kzen.auto.server.objects.job.worker.WorkerBase.payloadFlow] mapping input to
 * output. Covers the typed chain (FormulaSource streams Int -> Formula `payload:` maps Int -> Filter / sink
 * identity — every card would show the Int chip), a broken expression surfacing as that Worker's validation
 * ERROR (not a crash), a nested-Logic RunWorker typed by its Script callee's declared `results` signature, and
 * the skip lane (a manually-wired CSV pipeline: unknown columns -> no static validation, so no false errors).
 * Drives the same synthesize + filter + instantiate steps as [JobValidator.execute] (and
 * [tech.kzen.auto.server.exec.job.JobRun]), without the detached/cache layers.
 */
class JobValidatorTest {
    //-----------------------------------------------------------------------------------------------------------------
    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun typedPayloadChainInfersEveryLane() {
        val validation = validate("test/job-payload-formula-test.yaml")

        // Source: (1..3) infers IntRange — an Iterable type, so the lane streams its element type.
        assertEquals(TypeMetadata.int, typeOf(validation, "main.workers/source"))
        // Formula `payload: payload * 10` over an Int receiver infers Int.
        assertEquals(TypeMetadata.int, typeOf(validation, "main.workers/transform"))
        // Filter and sink forward the lane (identity) — their cards show the same type.
        assertEquals(TypeMetadata.int, typeOf(validation, "main.workers/filter"))
        assertEquals(TypeMetadata.int, typeOf(validation, "main.workers/collect"))

        assertEquals(
            listOf(), validation.workerValidations.values.mapNotNull { it.errorMessage },
            "the whole chain validates clean")
    }


    @Test
    fun brokenExpressionIsValidationErrorNotCrash() {
        val validation = validate("test/job-validator-error-test.yaml")

        val source = validation.workerValidations[ObjectPath.parse("main.workers/source")]
        assertNotNull(source, "the broken Worker gets an entry")
        assertNotNull(source.errorMessage, "the compile failure is the Worker's validation error")
        assertNull(source.typeMetadata, "a broken expression types nothing")

        val collect = validation.workerValidations[ObjectPath.parse("main.workers/collect")]
        assertNotNull(collect)
        assertNull(collect.errorMessage, "only the broken Worker carries the error")
    }


    @Test
    fun runWorkerTypedByScriptCalleeDeclaredResult() {
        val validation = validate("test/job-run-host-test.yaml")

        // The child Script (script-engine-child-test) declares `results: main: kotlin.Int`.
        assertEquals(TypeMetadata.int, typeOf(validation, "main.workers/run"))
        // The downstream sink forwards it (identity).
        assertEquals(TypeMetadata.int, typeOf(validation, "main.workers/sink"))
    }


    @Test
    fun undeclaredResultComponentIsSinkValidationError() {
        // Strict Script parity: a ResultSink whose component (blank -> main) is not declared in the Job's
        // `results` signature map errors on ITS card; the well-formed source stays clean.
        val validation = validate("test/job-result-undeclared-test.yaml")

        val collect = validation.workerValidations[ObjectPath.parse("main.workers/collect")]
        assertNotNull(collect)
        assertEquals("No result type declared in the Job signature for 'main'", collect.errorMessage)

        val source = validation.workerValidations[ObjectPath.parse("main.workers/source")]
        assertNotNull(source)
        assertNull(source.errorMessage, "only the sink carries the error")
    }


    @Test
    fun manuallyWiredCsvLaneSkipsWithoutFalseErrors() {
        // job-filter-expression-test wires its channels MANUALLY (non-blank ports), so the order-driven
        // derivation contributes no connections: every lane is unknown, the Filter's `where` over runtime CSV
        // columns is not statically validated (its errors surface at run time, as before), and no payload
        // type shows.
        val validation = validate("test/job-filter-expression-test.yaml")

        for ((path, entry) in validation.workerValidations) {
            assertNull(entry.errorMessage, "no false static error on $path")
            assertNull(entry.typeMetadata, "no payload type on the flat lane $path")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun typeOf(validation: JobValidation, path: String): TypeMetadata? {
        val entry = validation.workerValidations[ObjectPath.parse(path)]
        assertNotNull(entry, "expected a validation entry for $path")
        return entry.typeMetadata
    }


    private fun validate(path: String): JobValidation {
        context = KzenAutoContext.forTest()
        val documentPath = DocumentPath.parse(path)

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val synthesis = JobChannelSynthesis(context.notationMetadataReader)
            .synthesize(graphDefinition, documentPath)
        val filteredDefinition = synthesis.graphDefinition.filterTransitive(documentPath)
        val graphInstance = GraphCreator.createGraph(filteredDefinition, context.graphEnvironment)

        return JobValidator.validate(documentPath, graphDefinition.graphStructure, graphInstance)
    }
}
