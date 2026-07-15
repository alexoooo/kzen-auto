package tech.kzen.auto.server.exec.script

import tech.kzen.auto.common.objects.document.script.model.ScriptDependencyAnalysis
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * The steps whose *value* something reads, so a step that collects can skip the work when nothing will look
 * (see [tech.kzen.auto.server.objects.script.api.StepExecution.isValueReferenced]). Compile-time and static: a
 * runtime probe would prove nothing, because an expression step resolves EVERY in-scope value whether its code
 * names it or not (`StepExpressionSupport.evaluate`).
 *
 * Two things the notation-level [ScriptDependencyAnalysis] cannot supply on its own:
 *
 * 1. **Branch terminals.** A branch's last step becomes its container's value (`IfStep` returns it; `ForEachStep`
 *    collects it), but that is structural containment, which the analysis deliberately excludes from its data-dep
 *    edges. The ROOT step list is the exception and the reason this pays: `ScriptLogic.run` DISCARDS
 *    `runSteps(rootStepLocations)`'s value (the result comes from a `ResultStep` — there is no last-step
 *    fallback), so a trailing top-level loop is genuinely unread.
 * 2. **A completeness check.** The analysis enumerates branches by a hardcoded attribute-name list
 *    (`branchAttributeNames`), so a step type branching under any other name is invisible to it and its edges
 *    silently vanish. For the client's overlay that costs a missing arrow; here it would silently elide a value
 *    that IS read — wrong results, not a cosmetic gap.
 *
 * Both are resolved by walking [ScriptStep.nestedStepLists] — the same authoritative, per-type, no-`when`
 * enumeration the spine itself uses (`ScriptRunContext.nestedStableIds`). Any step it reaches that the analysis
 * never classified means the analysis is incomplete for this document, and every step is reported referenced.
 * Uncertainty always resolves to "referenced": over-reporting costs a list nobody reads, under-reporting is a
 * silently wrong run.
 */
object ScriptValueReferences {
    fun analyze(
        documentPath: DocumentPath,
        graphDefinition: GraphDefinition,
        graphInstance: GraphInstance,
        rootStepLocations: List<ObjectLocation>
    ): Set<ObjectLocation> {
        val analysis = ScriptDependencyAnalysis.analyze(graphDefinition, documentPath)

        val referenced = analysis.valueReferencedSources().toMutableSet()
        val allSteps = mutableSetOf<ObjectLocation>()
        var complete = true

        fun walk(steps: List<ObjectLocation>) {
            for (step in steps) {
                if (! allSteps.add(step)) {
                    continue
                }
                val instance = graphInstance[step]?.reference as? ScriptStep
                if (instance == null) {
                    // Not a step the graph could instantiate — the walk can't see past it, so don't trust the
                    // analysis for this document either.
                    complete = false
                    continue
                }
                for (nestedSteps in instance.nestedStepLists()) {
                    nestedSteps.lastOrNull()?.let { referenced.add(it) }
                    walk(nestedSteps)
                }
            }
        }
        walk(rootStepLocations)

        if (! complete || allSteps.any { it !in analysis.branchOfStep }) {
            return allSteps
        }

        return referenced
    }
}
