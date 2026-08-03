package tech.kzen.auto.server.exec.flow

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.script.test.ContextProbeLog
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs


/**
 * The Flow flavour's context signature (CX9): a Flow declares `context.requires` / `context.exports` on its
 * document root, and its logic-host vertices carry the same `contexts:` call-site map a Script's `RunStep`
 * does.
 *
 * **What a Flow deliberately does NOT get, and why the fixtures look the way they do.** A Flow has no
 * per-vertex `binds` / `uses` / `releases`. That is a decision, not an omission (CX doc §3 J verdict): a
 * Script's availability analysis threads ONE mutable set through a linear walk, and "before" has no meaning on
 * a DAG — porting it would require a fan-in join policy nothing in the arc has decided. So a Flow's whole
 * context story is what crosses its boundary, which is exactly what these fixtures exercise: it REQUIRES from
 * its caller, RELAYS an export upward, and SUPPLIES its callees. It never opens anything.
 *
 * The chain fixture is the headline. `Script -> Flow -> Script`, with a rename at every hop, ending in the
 * **unedited CX7b callee** — `script-context-call-callee-test.yaml`, reused verbatim from the Script suite.
 * That reuse is load-bearing: the innermost document was written before Flow had any of this and knows
 * nothing about it, so a passing read there cannot be an artefact of a fixture written to pass.
 *
 * Shares the process-global [ContextProbeLog] and resets it per run, so it relies on the suite's sequential
 * execution (as the other static-fixture engine tests do).
 */
class FlowContextTest {
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
    fun aFlowRelaysAContextFromItsCallerToTheLogicItsVertexRuns() {
        val outcome = runLogic("test/flow/context/flow-context-chain-test.yaml")

        assertIs<Outcome.Success>(outcome, (outcome as? Outcome.Failed)?.message)
        assertEquals(
            listOf(
                "provide[subject-a] saw nothing",
                "require saw subject-a"),
            ContextProbeLog.entries().filterNot { it.startsWith("disposed") },
            "three documents, two renames, one value: the caller holds `call-sut:a`, the Flow requires " +
                    "`call-driver`, and the innermost callee reads exact `call-sut`. No two of those keys " +
                    "answer to each other, so the ambient walk can supply none of it — each hop's " +
                    "`contexts:` map is the only path the value could have taken")
    }


    @Test
    fun aFlowDeclaringRequiresFailsAtRunStartWhenNobodySuppliesIt() {
        val outcome = runLogic("test/flow/context/flow-context-unsupplied-test.yaml")

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "Requires Callee driver slot: not provided by caller")
        assertEquals(listOf(), ContextProbeLog.entries(),
            "the gate fires before the DAG walk starts, so no vertex ran — the same run-start contract a " +
                    "Script's requires has, and what makes the declaration meaningful rather than decorative")
    }


    @Test
    fun aFlowsExportRelaysAChildsProvideToTheFlowsOwnCaller() {
        val outcome = runLogic("test/flow/context/flow-context-export-test.yaml")

        assertIs<Outcome.Success>(outcome, (outcome as? Outcome.Failed)?.message)
        assertEquals(
            listOf(
                "provide[relayed] saw nothing",
                "require saw relayed",
                "disposed[relayed]"),
            ContextProbeLog.entries(),
            "the read happens AFTER the Flow settled, so the binding outlived the frame that hosted its " +
                    "opener — it climbed two export declarations to rest on the outer caller. The single " +
                    "trailing disposal says it came to rest exactly once, at that caller's settle")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Compiles through [LogicCompiler] rather than FlowLogicCompiler directly: two of the three fixtures have a
    // SCRIPT root that hosts the Flow, so the flavour dispatch is itself part of what is exercised.
    private fun runLogic(documentPathString: String): Outcome {
        ScriptStepTestModule.register()
        ContextProbeLog.reset()

        context = KzenAutoContext.forTest()

        val documentPath = DocumentPath.parse(documentPathString)
        val mainLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))

        val runNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(runNotation).transitiveSuccessful

        val logic = LogicCompiler.compile(
            mainLocation,
            runNotation,
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

        val engine = RunEngine(logic, context.objectStableMapper.objectStableId(mainLocation), TupleValue.empty)
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
}
