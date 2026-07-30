package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * Live-edit migration coverage for the Job SIGNATURE (J2, logic-spec §5): pause a parameterized Job mid-stream,
 * edit config, resume — and the harvested result must still be COMPLETE. It is the whole load-bearing proof of
 * the FormulaSource stream cursor + the ResultSink carry-without-clear: the sink's final seen-count must be
 * EXACTLY the stream size (a source restart duplicates the prefix — overshoot; a dropped in-flight element or a
 * lost accumulation falls short), and the kept-last result must be the stream's final element. Modeled
 * line-for-line on [JobMigrationTest.migrationResumesReaderAndCarriesPreviewStateLosslessly] (which pins the
 * channel's FIFO order carry on an order-observable lane).
 */
class JobSignatureMigrationTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/job/signature/job-signature-child-test.yaml")
    private val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
    private val sinkLocation = ObjectLocation(documentPath, ObjectPath.parse("main.workers/collect"))
    private val total = 50

    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun pauseEditResumeKeepsResultComplete() {
        context = KzenAutoContext.forTest()

        val notation = AutoTestUtils.readNotation()
        val baseLogic = compile(notation)
        // Editing the sink's `result` "" -> "main" is semantically neutral (same component either way) but
        // value-differs the definition, so it drives a real rebuild — the migration must then carry the source
        // cursor, the in-flight channel payloads, AND the sink's accumulation across the cut.
        val editedLogic = compile(edit(notation, sinkLocation, "result", "main"))

        val rootInputs = TupleValue(listOf(
            TupleComponentValue(TupleComponentName("items"), (0 until total).toList())))

        val engine = RunEngine(baseLogic, context.objectStableMapper.objectStableId(jobLocation), rootInputs)
        try {
            // Step to a paused wavefront where the sink has collected SOME but not all — so resume-vs-restart and
            // the in-flight carry are observable (batchSize 1 makes one element roughly one step).
            var guard = 0
            do {
                engine.step()
                engine.awaitQuiescent()
                guard += 1
            }
            while (collectedCount(engine) == 0L && guard < 500)
            val midCount = collectedCount(engine)
            assertTrue(
                midCount in 1 until total.toLong(),
                "the sink should have collected some (but not all) rows before the edit (was $midCount)")

            engine.migrate(editedLogic, paused = false)
            val outcome = runBlocking { engine.await() }

            val success = assertIs<Outcome.Success>(outcome)
            assertEquals(
                total - 1, success.value.mainComponentValue(),
                "the kept-last result is the stream's final element")
            assertEquals(
                total.toLong(), collectedCount(engine),
                "every element seen exactly once: a source restart would duplicate the prefix (overshoot), a " +
                    "dropped in-flight element or a lost carried count would fall short")
        }
        finally {
            engine.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun compile(notation: GraphNotation): JobLogic {
        val definition = AutoTestUtils.graphDefinitionAttempt(notation).transitiveSuccessful
        return JobLogicCompiler.compile(
            jobLocation,
            notation,
            definition,
            LogicCompilerServices(
                context.graphEnvironment,
                context.objectStableMapper,
                context.cachedKotlinCompiler,
                context.scriptValidationCache,
                context.jobValidationCache,
                context.notationMetadataReader,
                context.jobWorkPool,
                LogicRunExecutionId.random()))
    }


    private fun edit(
        notation: GraphNotation,
        objectLocation: ObjectLocation,
        attribute: String,
        value: String
    ): GraphNotation =
        NotationReducer()
            .applyStructural(
                notation,
                UpsertAttributeCommand(
                    objectLocation, AttributeName(attribute), ScalarAttributeNotation(value)))
            .graphNotation


    // The ResultSink Worker's live collected count, read from its node's progress emit (the engine's in-tree
    // analogue of the trace path the JS Job UI polls). The migrated sink is a fresh node with the SAME stable id.
    private fun collectedCount(engine: RunEngine): Long {
        val sinkStableId = context.objectStableMapper.objectStableId(sinkLocation)
        val sinkNode = engine.snapshot().root.children
            .firstOrNull { it.stableId == sinkStableId }
            ?: return 0L
        val progress = sinkNode.live[Address.of(EngineJobControl.workerProgressAddressMarker)]?.get() as? Map<*, *>
            ?: return 0L
        return progress["collected"] as? Long ?: 0L
    }
}
