package tech.kzen.auto.server.exec.flow

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.flow.test.CountingSinkVertex
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
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
 * Engine-side coverage of the Flow flavour's live-edit state migration (logic-spec §5): pause -> edit config ->
 * resume, driven directly through [RunEngine.migrate] (exactly what the controller's edit-detection does). A
 * mid-DAG edit RESUMES from the carried per-vertex progress ([FlowRun] capturing / restoring [FlowMigrationState])
 * rather than restarting.
 *
 * The signal is the [CountingSinkVertex.processed] tally: a fully replayable Flow yields the same final output
 * whether progress carries or restarts (a restarted source just re-emits), so only the count of `process`
 * invocations is observable. With a 1..5 source, a lossless carry processes every value exactly once -> the
 * count settles at EXACTLY 5; a restart-instead-of-resume re-processes the already-consumed prefix and exceeds
 * 5; a dropped value would fall short. The assertion is therefore exact and timing-independent.
 */
class FlowMigrationTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/flow-migration-test.yaml")
    private val flowLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
    private val sinkLocation = ObjectLocation(documentPath, ObjectPath.parse("FmSink"))
    private val sourceTotal = 5

    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun editingMidDagResumesFromCarriedProgressProcessingEachValueOnce() {
        CountingSinkVertex.reset()
        context = KzenAutoContext.forTest()

        val notation = AutoTestUtils.readNotation()
        val baseLogic = compile(notation)
        // A no-op change to the SINK's `note` trips the rebuild without touching the source's position.
        val editedLogic = compile(edit(notation, sinkLocation, "note", "edited"))

        val engine = RunEngine(baseLogic, context.objectStableMapper.objectStableId(flowLocation))
        try {
            // Step the DAG to a paused wavefront where the sink has processed part (but not all) of the stream, so
            // resume-vs-restart is observable across the edit.
            var guard = 0
            do {
                engine.step()
                engine.awaitQuiescent()
                guard += 1
            }
            while (CountingSinkVertex.processed.get() < 2 && guard < 200)

            val processedBeforeEdit = CountingSinkVertex.processed.get()
            assertTrue(
                processedBeforeEdit in 2 until sourceTotal,
                "sink should process part (not all) of the stream before the edit (was $processedBeforeEdit)")

            // Resume against the edited definition: the source resumes from its stream position and the sink
            // continues its accumulator, so the remaining values are processed exactly once.
            engine.migrate(editedLogic, paused = false)
            val outcome = runBlocking { engine.await() }

            assertIs<Outcome.Success>(outcome)
            assertEquals(
                sourceTotal, CountingSinkVertex.processed.get(),
                "the migration carries per-vertex progress, so every value is processed exactly once across the " +
                    "edit (a restart would re-process the $processedBeforeEdit-value prefix and exceed $sourceTotal)")
        }
        finally {
            engine.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun compile(notation: GraphNotation): FlowLogic {
        val definition = AutoTestUtils.graphDefinitionAttempt(notation).transitiveSuccessful
        return FlowLogicCompiler.compile(
            flowLocation,
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
}
