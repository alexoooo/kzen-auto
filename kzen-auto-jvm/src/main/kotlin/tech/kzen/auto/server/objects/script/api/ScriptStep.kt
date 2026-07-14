package tech.kzen.auto.server.objects.script.api

import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * A Script step — a notation object that both VALIDATES (its [definition] type powers in-script typing) and
 * EXECUTES (its [run] is invoked polymorphically by the engine spine). A step is third-party-extensible: a
 * plugin adds one as an `@Reflect` class declared `is: ScriptStep`, with no change to any kzen dispatch —
 * exactly like a `FlowVertex` or a `Worker`. The framework spine ([StepExecution.runSteps]) owns the uniform
 * per-step boundary / trace / replay lifecycle; a step only computes its value.
 */
interface ScriptStep {
    fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition?


    /**
     * Execute this step against the per-run [execution], returning the value it produces (null = void / no value).
     * A control step composes nested branches via [StepExecution.runSteps]; a nested-logic step hosts a child via
     * [StepExecution.host]; an expression step compiles its own code with its injected services.
     *
     * Abstract — a step type MUST declare its execution; there is no hidden fallback (that is what makes the step
     * set extensible without a central dispatch). A referenceable-but-not-executed notation object (a parameter /
     * loop-item binding) is a [ScriptValueBinding], whose `run` explicitly rejects execution.
     */
    suspend fun run(execution: StepExecution): Any?


    /**
     * The nested step-list branches this step owns (an If's then/else, a loop's body), or empty for a leaf.
     * The spine recurses through these to expand the live-edit replay set ([StepExecution.dropReplay]); a control
     * step overrides to expose its branches so the mechanism stays generic — no per-type knowledge in the spine.
     *
     * A LOOP step additionally flags the branch it re-runs as `rerun: true` in its `meta.<branch>` notation
     * metadata (e.g. ForEachStep/DoWhileStep `meta.steps.rerun`) and consumes a control signal targeting itself
     * via [StepExecution.consumeLoopSignal] after running that branch. A third-party loop step joins loop
     * semantics declaratively by declaring both — ControlStep may then target it and move-to jumps into its body
     * are excluded — with no change to shared code (read by `ScriptNestingAnalysis`).
     */
    fun nestedStepLists(): List<List<ObjectLocation>> {
        return listOf()
    }
}
