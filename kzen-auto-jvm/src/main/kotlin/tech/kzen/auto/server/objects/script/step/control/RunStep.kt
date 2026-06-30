package tech.kzen.auto.server.objects.script.step.control

import tech.kzen.auto.common.objects.document.script.ResultSignatureDefiner
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class RunStep(
    private val instructions: ObjectLocation,
    @Suppress("unused") private val arguments: Map<String, ObjectLocation>,
    @Suppress("unused") private val selfLocation: ObjectLocation
):
    ScriptStep
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        // A RunStep yields whatever its linked logic returns. For a sub-Script the output type is declared:
        // its `results` signature (void when none is declared), so surface that rather than a blanket Any.
        // For any other linked logic (e.g. a Flow) the output type isn't declared anywhere we can read, so
        // fall back to Any instead of mislabelling it void.
        val graphNotation = scriptDefinitionContext.graphNotation
        val instructionsDocument = graphNotation.documents[instructions.documentPath]

        val returnSignature =
            if (instructionsDocument != null && ScriptConventions.isScript(instructionsDocument)) {
                ResultSignatureDefiner.parse(
                    graphNotation.firstAttribute(instructions, ScriptConventions.resultsAttributePath))
            }
            else {
                TupleDefinition.ofMain(LogicType.any)
            }

        return ScriptStepDefinition.of(returnSignature)
    }
}
