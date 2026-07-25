package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


/**
 * End-to-end for the element model (`JobMessage` — see the model appendix of
 * kzen/plans/2026-07-25_job-improvements.md; original design in
 * kzen/plans/sprint-2/2026-07-21_job-element-model.md): the
 * uniform message carrier crossing every Job channel, with the auto-flatten fallback bridging payload lanes
 * into the column-based Workers. Three lanes, each a real notation on the engine:
 *
 * - **The reproducing shape** (the user's Script-2 → Job-3): a scalar parameter payload through a FormulaWorker
 *   into a ResultSink — previously a `Double cannot be cast to DataRecord` run failure, now completes with the
 *   PAYLOAD yielded (boundary rule: payload wins, flat columns are auxiliary).
 * - **Scalar auto-flatten**: a Double stream through the expression Filter (referencing the flattened `value`
 *   column) into a CsvWriter — palette-insert-and-it-works over a payload lane.
 * - **Map auto-flatten**: Map payloads flatten to keyed columns for the CsvWriter.
 * - **Parameter scope** (phase 2): a declared typed parameter gates a Filter `where` bare by name — the
 *   declared default when the run binds nothing, the bound argument when it does.
 * - **Typed payload chain** (phase 3): a FormulaWorker `payload:` expression transforms each payload with the
 *   lane's inferred type as the bare receiver, and a FilterWorker `where` reads the typed `payload` alias —
 *   the static payload-type walk threading receivers through the whole chain.
 */
class JobElementModelTest {
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
    fun scalarParameterPayloadPassesThroughFormulaToResult() {
        // Mirror of Script-2 → Job-3: the Formula's `foo: 2 + 2` evaluates against the auto-flattened message
        // (appending a flat column) while the payload rides through untouched — so the Job yields 13.0, not a
        // record, and no cast failure occurs anywhere.
        val engine = newEngine(
            "test/job-message-parameter-test.yaml",
            TupleValue(listOf(TupleComponentValue(TupleComponentName("number"), 13.0))))
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

        val success = assertIs<Outcome.Success>(outcome)
        assertEquals(13.0, success.value.mainComponentValue())
    }


    @Test
    fun declaredParameterGatesFilterViaDefault() {
        // Run BARE: the Filter's `value.number > threshold` reads the DECLARED default (2), keeping 30.0 and
        // 2.5 — the sink keeps the LAST survivor (default `keep`).
        val engine = newEngine("test/job-parameter-scope-test.yaml")
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

        val success = assertIs<Outcome.Success>(outcome, "outcome: $outcome")
        assertEquals(2.5, success.value.mainComponentValue())
    }


    @Test
    fun boundArgumentOverridesDeclaredDefault() {
        // The bound `threshold` argument (3) wins over the declared default (2): only 30.0 passes, which the
        // sink keeps as the result.
        val engine = newEngine(
            "test/job-parameter-scope-test.yaml",
            TupleValue(listOf(TupleComponentValue(TupleComponentName("threshold"), 3))))
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

        val success = assertIs<Outcome.Success>(outcome, "outcome: $outcome")
        assertEquals(30.0, success.value.mainComponentValue())
    }


    @Test
    fun payloadExpressionTransformsTypedStream() {
        // Phase 3: FormulaSource (1..3) streams Int payloads; the Formula's `payload: payload * 10` maps each
        // (bare Int arithmetic — the receiver is the lane's inferred type); the Filter's `where: payload > 15`
        // reads the typed payload; the sink keeps the last surviving transformed payload.
        val engine = newEngine("test/job-payload-formula-test.yaml")
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

        val success = assertIs<Outcome.Success>(outcome, "outcome: $outcome")
        assertEquals(30, success.value.mainComponentValue())
    }


    @Test
    fun scalarStreamFiltersAndWritesViaAutoFlatten() {
        // Doubles 1.5 / 30.0 / 2.5 stream as payload messages; the Filter's `value.number > 2` reads the
        // auto-flattened `value` column (ColumnValue.toText renders 30.0 as "30"); the CsvWriter writes the
        // same flat part.
        val output = Path.of("build/job-message-flatten/output.csv")
        Files.createDirectories(output.parent)
        Files.deleteIfExists(output)

        val engine = newEngine("test/job-message-flatten-test.yaml")
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

        assertIs<Outcome.Success>(outcome, "outcome: $outcome")
        assertEquals(listOf("value", "30", "2.5"), Files.readAllLines(output))
    }


    @Test
    fun mapPayloadsFlattenToKeyedColumns() {
        val output = Path.of("build/job-message-map-flatten/output.csv")
        Files.createDirectories(output.parent)
        Files.deleteIfExists(output)

        val engine = newEngine("test/job-message-map-flatten-test.yaml")
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

        assertIs<Outcome.Success>(outcome, "outcome: $outcome")
        assertEquals(
            listOf("city,amount", "Lviv,30", "Kyiv,40"),
            Files.readAllLines(output))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun newEngine(path: String, rootInputs: TupleValue = TupleValue.empty): RunEngine {
        context = KzenAutoContext.forTest()

        val documentPath = DocumentPath.parse(path)
        val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

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

        return RunEngine(jobLogic, context.objectStableMapper.objectStableId(jobLocation), rootInputs)
    }
}
