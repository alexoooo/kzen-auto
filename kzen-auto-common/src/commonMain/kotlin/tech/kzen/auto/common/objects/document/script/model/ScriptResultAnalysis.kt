package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.auto.common.objects.document.logic.ResultSignatureDefiner
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions


/**
 * Where a Script's result comes from, read from notation alone (no definition, no instantiation) so the server
 * validator and the client's Result marker cannot drift.
 *
 * A Script that runs to its end yields its last root step's value, mirroring how an IfStep returns its taken
 * branch's terminal and a ForEachStep collects its body's. A Result step supplies the value instead, and ends the
 * Script — so root steps after a root-level Result step never run.
 */
data class ScriptResultAnalysis(
    val rootSteps: List<ObjectLocation>,
    val unreachableRootSteps: List<ObjectLocation>,

    /** The step whose value becomes the result implicitly: the last reachable root step when it is not itself a
     * Result step. Null when the Script is void, has no steps, or ends in a Result step. */
    val implicitResultStep: ObjectLocation?,

    val declaresMainResult: Boolean
) {
    companion object {
        fun analyze(graphNotation: GraphNotation, documentPath: DocumentPath): ScriptResultAnalysis {
            val mainLocation = documentPath.toObjectLocation(NotationConventions.mainObjectPath)

            val rootSteps = ScriptConventions.orderedDirectChildLocations(
                graphNotation,
                AttributeLocation(mainLocation, ScriptConventions.stepsAttributePath))

            val declaresMainResult = ResultSignatureDefiner
                .parse(graphNotation.firstAttribute(mainLocation, ScriptConventions.resultsAttributePath))
                .find(TupleComponentName.main) != null

            // A root-level Result step ends the Script, so everything after it never runs. Reachability only —
            // NOT a type exemption: the terminal below is held to the declared result type either way.
            val endIndex = rootSteps.indexOfFirst { ScriptConventions.isResultStep(graphNotation, it) }
            val reachableRootSteps =
                if (endIndex < 0) { rootSteps }
                else { rootSteps.subList(0, endIndex + 1) }
            val unreachableRootSteps =
                if (endIndex < 0) { listOf() }
                else { rootSteps.subList(endIndex + 1, rootSteps.size) }

            val terminal = reachableRootSteps.lastOrNull()
            val implicitResultStep = terminal?.takeIf {
                declaresMainResult && !ScriptConventions.isResultStep(graphNotation, it)
            }

            return ScriptResultAnalysis(
                rootSteps, unreachableRootSteps, implicitResultStep, declaresMainResult)
        }
    }
}
