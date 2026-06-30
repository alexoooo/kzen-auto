package tech.kzen.auto.server.exec

import tech.kzen.auto.common.objects.document.flow.FlowConventions
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.server.exec.flow.FlowLogicCompiler
import tech.kzen.auto.server.exec.job.JobLogicCompiler
import tech.kzen.auto.server.exec.script.ScriptLogicCompiler
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation


/**
 * Translates the Logic document at [location] into an engine [Logic], dispatching on its flavour (the main
 * object's `is:` type): Script → [ScriptLogicCompiler], Flow → [FlowLogicCompiler], Job → [JobLogicCompiler].
 * This is the single entry point the [tech.kzen.auto.server.service.impl.ServerLogicController] starts a run
 * through, and the seam a nested [tech.kzen.auto.server.exec.script.step.RunStep] /
 * [tech.kzen.auto.server.objects.flow.vertex.RunLogicVertex] compiles its child through — so the flavours nest
 * each other uniformly.
 *
 * A document of any other type throws [NotImplementedError] (the controller turns that into a clean
 * failure-to-start).
 */
object LogicCompiler {
    fun compile(
        location: ObjectLocation,
        graphNotation: GraphNotation,
        graphDefinition: GraphDefinition,
        services: LogicCompilerServices
    ): Logic {
        val documentNotation = graphNotation.documents[location.documentPath]
            ?: throw IllegalArgumentException("Document not found: ${location.documentPath}")

        return when {
            ScriptConventions.isScript(documentNotation) ->
                ScriptLogicCompiler.compile(location, graphNotation, graphDefinition, services)

            FlowConventions.isFlow(documentNotation) ->
                FlowLogicCompiler.compile(location, graphNotation, graphDefinition, services)

            JobConventions.isJob(documentNotation) ->
                JobLogicCompiler.compile(location, graphNotation, graphDefinition, services)

            else ->
                throw NotImplementedError("Logic flavour not supported in engine translation: $location")
        }
    }
}
