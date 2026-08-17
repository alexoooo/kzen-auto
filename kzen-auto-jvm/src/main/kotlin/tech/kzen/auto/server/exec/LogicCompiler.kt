package tech.kzen.auto.server.exec

import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.context.GraphCreator


/**
 * Translates the Logic document at [location] into an engine [Logic] by resolving the document's `main`
 * archetype and letting it compile itself: the archetype (`is: Script` / `Flow` / `Job` / `Report`, or any
 * third-party paradigm) implements [LogicDocument] and provides its own [Logic] via [LogicDocument.toLogic].
 * There is no flavour `when` here — a new paradigm is added purely as a notation archetype implementing
 * [LogicDocument], never by editing this class (the same rule that makes [ScriptStep][tech.kzen.auto.server.objects.script.api.ScriptStep]
 * extensible, applied one level up).
 *
 * This is the single entry point the [tech.kzen.auto.server.service.impl.ServerLogicController] starts a run
 * through, and the seam a nested [RunStep][tech.kzen.auto.server.objects.script.step.control.RunStep] /
 * [RunLogicVertex][tech.kzen.auto.server.objects.flow.vertex.RunLogicVertex] compiles its child through — so the
 * flavours nest each other uniformly, **Report included**. There is no top-level-only flavour and no exception
 * to the uniformity: any document whose `main` is a [LogicDocument] is hostable, which is what makes the absent
 * flavour `when` above a design point rather than an oversight (pinned by
 * [ReportHostedTest][tech.kzen.auto.server.exec.report.ReportHostedTest]).
 *
 * The archetype is instantiated from a graph filtered to [location] alone: its nested children (a Job's Workers,
 * a Script's steps, a Flow's vertices) are weak `NestedList` references, so they are excluded and never
 * constructed here. That matters for Job in particular — its saved Worker channel ports are blank and
 * unsatisfiable until [JobChannelSynthesis][tech.kzen.auto.common.objects.document.job.JobChannelSynthesis] fills
 * them, so building the whole document graph would throw; building the archetype alone does not. A document whose
 * `main` is not a [LogicDocument] throws [NotImplementedError] (the controller turns that into a clean
 * failure-to-start).
 */
object LogicCompiler {
    fun compile(
        location: ObjectLocation,
        graphNotation: GraphNotation,
        graphDefinition: GraphDefinition,
        services: LogicCompilerServices
    ): Logic {
        val archetype = GraphCreator
            .createGraph(graphDefinition.filterTransitive(location), services.graphEnvironment)[location]
            ?.reference

        val logicDocument = archetype as? LogicDocument
            ?: throw NotImplementedError(
                "Not a runnable Logic document: $location (${archetype?.let { it::class.simpleName }})")

        return logicDocument.toLogic(location, graphNotation, graphDefinition, services)
    }
}
