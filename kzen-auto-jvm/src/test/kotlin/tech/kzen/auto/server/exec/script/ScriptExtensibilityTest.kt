package tech.kzen.auto.server.exec.script

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.script.test.ResourceDisposalLog
import tech.kzen.auto.server.exec.script.test.ScriptStepTestModule
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


/**
 * The extensibility acceptance test for the Script step redesign, and the companion flavour-dispatch fix (A4/A5):
 *
 * - [thirdPartyStepRunsWithNoCompilerChange] runs a Script containing [ShoutStep][tech.kzen.auto.server.exec.script.test.ShoutStep]
 *   — a step type defined ENTIRELY in the test source set and registered by hand ([ScriptStepTestModule]), with
 *   no `@Reflect`, no entry in [ScriptLogicCompiler] and no kzen `when`. It compiles through [LogicCompiler]
 *   (which resolves the Script archetype polymorphically as a [LogicDocument][tech.kzen.auto.server.exec.LogicDocument])
 *   and runs on the real [RunEngine]. If a third-party step needs no kzen edit to run, the step set is genuinely
 *   extensible — the defect the redesign set out to fix.
 *
 * - [resourcesDisposedPerClosePolicyOnSuccess] / [keepOnFailureResourceRetainedOnFailure] check the run-scoped
 *   resource registry ([StepExecution.openResource][tech.kzen.auto.server.objects.script.api.StepExecution.openResource])
 *   disposes each resource per its [ResourceClosePolicy][tech.kzen.lib.common.exec.logic.ResourceClosePolicy] when
 *   the run settles: Auto always, Manual never (auto), KeepOnFailure only when the run did not fail.
 *
 * - [exportedResourceOutlivesOpenerAndDisposesAtItsRestingFrame] / [aTwoHopExportChainRestsAtTheRoot] check
 *   export-chain ownership (logic-spec §6): a resource a child sub-Script opens climbs one frame per
 *   `context.exports` declaration and rests at the first frame that does not export it, so it outlives the
 *   opener's own settle and disposes only when that frame settles. Both fixtures open through the RAW string
 *   API, which pins the interop half too: the export names a typed `Context` whose `key` is that same plain
 *   string.
 *
 * - [openResourceSurvivesLiveEditMigration] / [exportedResourceSurvivesMigrationOnItsRestingFrame] check the
 *   live-edit barrier (logic-spec §5 "open resources"): a resource opened before a [RunEngine.migrate] is NOT
 *   disposed by the teardown — its registration is lifted keyed by its OWNER's stable id and re-adopted there —
 *   and its handle is still readable afterward (the "edit-while-paused quits the browser" regression case).
 *
 * The tests share the process-global [ResourceDisposalLog] and reset it per run, so they rely on the suite's
 * sequential execution (as the other static-fixture engine tests do).
 */
class ScriptExtensibilityTest {
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
    fun thirdPartyStepRunsWithNoCompilerChange() {
        val outcome = runScript("test/script-extensibility-test.yaml")
        assertEquals("HELLO!!!", assertIs<Outcome.Success>(outcome).value.mainComponentValue())
    }


    @Test
    fun resourcesDisposedPerClosePolicyOnSuccess() {
        val outcome = runScript("test/script-resource-success-test.yaml")
        assertIs<Outcome.Success>(outcome)
        assertEquals(setOf("auto", "keep"), ResourceDisposalLog.disposed())
    }


    @Test
    fun keepOnFailureResourceRetainedOnFailure() {
        val outcome = runScript("test/script-resource-failure-test.yaml")
        assertIs<Outcome.Failed>(outcome)
        assertEquals(setOf("auto"), ResourceDisposalLog.disposed())
    }


    @Test
    fun exportedResourceOutlivesOpenerAndDisposesAtItsRestingFrame() {
        // A child sub-Script opens `sut` and exports the TestSutContext whose key that is; the parent exports
        // nothing, so the chain stops there. The parent's AssertDisposedStep (which runs after the child
        // settled) would throw if `sut` were already disposed, so a Success proves it outlived the opener, and
        // the disposal set proves it was disposed when the frame it rests on settled.
        val outcome = runScript("test/script-resource-parent-scope-test.yaml")
        assertIs<Outcome.Success>(outcome)
        assertEquals(setOf("sut"), ResourceDisposalLog.disposed())
    }


    @Test
    fun aTwoHopExportChainRestsAtTheRoot() {
        // root → mid → leaf (opens `sut`); the leaf and the mid both export, the root does not.
        // AssertDisposedStep in both mid (after the leaf settled) and root (after mid settled) would throw if it
        // had been disposed early, so a Success proves each declaration carried ownership one frame further, and
        // the disposal set proves it was disposed at the root settle.
        val outcome = runScript("test/script-resource-run-scope-test.yaml")
        assertIs<Outcome.Success>(outcome)
        assertEquals(setOf("sut"), ResourceDisposalLog.disposed())
    }


    @Test
    fun openResourceSurvivesLiveEditMigration() {
        // The rebuilt run replays the completed opening step without re-executing it, so the ReadResourceStep
        // would throw if the barrier had disposed the handle; Success proves the lifted registration was
        // re-adopted with its value, and the disposal set proves the closer still fired (exactly once) when the
        // run settled.
        val outcome = migrateAtBarrier("test/script-resource-migration-test.yaml", stepsToBarrier = 2)

        assertIs<Outcome.Success>(outcome)
        assertEquals(setOf("sut"), ResourceDisposalLog.disposed(),
            "the surviving resource is disposed exactly once, at the run's settle")
    }


    @Test
    fun exportedResourceSurvivesMigrationOnItsRestingFrame() {
        // Same barrier, but the child that opened the resource has already settled when it is reached: the
        // export moved ownership to the root, so the lift keys the registration by the ROOT's stable id.
        // Success proves the root re-adopted it (the Read there finds the live handle), and the disposal set
        // proves it disposes at that resting frame and nowhere else.
        val outcome = migrateAtBarrier("test/script-resource-export-migration-test.yaml", stepsToBarrier = 3)

        assertIs<Outcome.Success>(outcome)
        assertEquals(setOf("sut"), ResourceDisposalLog.disposed(),
            "the exported resource is disposed exactly once, at its resting frame's settle")
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Park the run at a boundary where an opened resource is still live, drive the live-edit barrier
     * ([RunEngine.migrate] with a recompiled Logic), then resume to a terminal outcome. [stepsToBarrier] counts
     * boundaries rather than steps: the first [RunEngine.step] parks BEFORE the first step, so it is one more
     * than the number of steps that must have run — and a RunStep contributes a boundary inside the document it
     * hosts.
     */
    private fun migrateAtBarrier(documentPathString: String, stepsToBarrier: Int): Outcome {
        ScriptStepTestModule.register()
        ResourceDisposalLog.reset()

        context = KzenAutoContext.forTest()

        val scriptLocation = ObjectLocation(
            DocumentPath.parse(documentPathString),
            ObjectPath.parse("main"))

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        fun compile() = LogicCompiler.compile(
            scriptLocation, graphNotation, graphDefinition, compilerServices())

        val engine = RunEngine(
            compile(), context.objectStableMapper.objectStableId(scriptLocation), TupleValue.empty)
        return try {
            repeat(stepsToBarrier) {
                engine.step()
                engine.awaitQuiescent()
            }
            assertEquals(emptySet<String>(), ResourceDisposalLog.disposed(),
                "the resource is open and undisposed at the migration barrier")

            engine.migrate(compile(), paused = false)
            runBlocking { engine.await() }
        }
        finally {
            engine.close()
        }
    }


    private fun runScript(documentPathString: String, inputs: TupleValue = TupleValue.empty): Outcome {
        ScriptStepTestModule.register()
        ResourceDisposalLog.reset()

        context = KzenAutoContext.forTest()

        val documentPath = DocumentPath.parse(documentPathString)
        val scriptLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val logic = LogicCompiler.compile(
            scriptLocation,
            graphNotation,
            graphDefinition,
            compilerServices())

        val engine = RunEngine(logic, context.objectStableMapper.objectStableId(scriptLocation), inputs)
        return try {
            runBlocking {
                engine.resume()
                engine.await()
            }
        }
        finally {
            engine.close()
        }
    }


    private fun compilerServices(): LogicCompilerServices {
        return LogicCompilerServices(
            context.graphEnvironment,
            context.objectStableMapper,
            context.cachedKotlinCompiler,
            context.scriptValidationCache,
            context.jobValidationCache,
            context.notationMetadataReader,
            context.jobWorkPool,
            LogicRunExecutionId.random())
    }
}
