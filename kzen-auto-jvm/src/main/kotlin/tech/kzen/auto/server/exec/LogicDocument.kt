package tech.kzen.auto.server.exec

import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation


/**
 * The extensibility seam for run flavours: a Logic document's `main` archetype (`is: Script` / `Flow` / `Job` /
 * `Report`, or any third-party paradigm) implements this to translate its own notation into an engine
 * [Logic]. [LogicCompiler] resolves the archetype instance polymorphically from the graph and calls [toLogic],
 * so adding a paradigm needs no change to [LogicCompiler] — exactly as a new [ScriptStep][tech.kzen.auto.server.objects.script.api.ScriptStep]
 * needs no change to the Script compiler. This is the flavour-level analogue of that step-level rule.
 *
 * The archetype delegates to its flavour compiler; the compiler stays the structure/signature pass it already
 * is. [location] is the document's `main` object location, [graphNotation] / [graphDefinition] the (successful,
 * transitive) build the run compiles from, and [services] the bundle every compiler threads through so a nested
 * [RunStep][tech.kzen.auto.server.objects.script.step.control.RunStep] / [RunLogicVertex][tech.kzen.auto.server.objects.flow.vertex.RunLogicVertex]
 * can compile an arbitrary child flavour uniformly.
 */
interface LogicDocument {
    fun toLogic(
        location: ObjectLocation,
        graphNotation: GraphNotation,
        graphDefinition: GraphDefinition,
        services: LogicCompilerServices
    ): Logic
}
