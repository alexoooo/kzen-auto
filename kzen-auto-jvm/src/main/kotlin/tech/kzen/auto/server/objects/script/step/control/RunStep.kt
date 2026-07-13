package tech.kzen.auto.server.objects.script.step.control

import tech.kzen.auto.common.objects.document.script.ResultSignatureDefiner
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class RunStep(
    private val instructions: ObjectLocation,
    private val arguments: Map<String, ObjectLocation>,
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    // Host the linked Logic ([instructions]) as a confined child node, resolving each declared argument from this
    // run's in-scope values at call time. The engine owns the child's stepping, so step-over / step-out cross the
    // boundary uniformly; the child result is this step's value (recorded + traced by the enclosing sequence).
    override suspend fun run(execution: StepExecution): Any? {
        val argumentValues = TupleValue(
            arguments.map { (name, argumentLocation) ->
                TupleComponentValue(TupleComponentName(name), execution.referencedValue(argumentLocation))
            })

        return execution.host(instructions, argumentValues)
            .mainComponentValue()
    }


    //-----------------------------------------------------------------------------------------------------------------
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
