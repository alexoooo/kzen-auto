package tech.kzen.auto.server.exec.script

import tech.kzen.auto.common.objects.document.logic.ResultSignatureDefiner
import tech.kzen.auto.common.objects.document.logic.BindingSignatureDefiner
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptResultAnalysis
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.exec.LogicParameter
import tech.kzen.auto.server.objects.script.ScriptValidator
import tech.kzen.lib.common.exec.engine.LogicFailure
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.context.GraphCreator


/**
 * Translates a Script document's notation graph into a runnable [ScriptLogic]. It reuses the existing
 * definition / validation wholesale (the graph is instantiated and validated exactly as the engine needs) and
 * then captures only the *structure*: the parameters, the result signature, and the document-ordered root step
 * locations ([ScriptConventions.orderedDirectChildLocations]).
 *
 * It does NOT enumerate step types. Each step is its own `@Reflect`
 * [tech.kzen.auto.server.objects.script.api.ScriptStep] archetype that the engine runs polymorphically (its
 * `run` resolved from the instantiated graph), exactly as Flow runs a `FlowVertex` and Job runs a `Worker`. So a
 * new step type — Browser, or a third-party step — needs no change here: it is added as a notation object that
 * implements `ScriptStep`, nothing more.
 */
object ScriptLogicCompiler {
    //-----------------------------------------------------------------------------------------------------------------
    fun compile(
        scriptLocation: ObjectLocation,
        graphNotation: GraphNotation,
        graphDefinition: GraphDefinition,
        services: LogicCompilerServices
    ): ScriptLogic {
        val documentPath = scriptLocation.documentPath

        val graphInstance = GraphCreator.createGraph(
            graphDefinition.filterTransitive(documentPath), services.graphEnvironment)
        val scriptTree = ScriptTree.read(documentPath, graphDefinition)
        val scriptValidation = services.scriptValidationCache.scriptValidation(documentPath, graphDefinition) {
            ScriptValidator.validate(
                documentPath, graphNotation, graphDefinition, graphInstance,
                services.cachedKotlinCompiler, scriptTree)
        }
        val resultSignature = ResultSignatureDefiner.parse(
            graphNotation.firstAttribute(scriptLocation, ScriptConventions.resultsAttributePath))
        val unsupportedResult = resultSignature.definitions.firstOrNull {
            it.name != BindingName("main")
        }
        if (unsupportedResult != null) {
            throw LogicFailure(
                "Script supports only the 'main' result; found '${unsupportedResult.name.value}'")
        }

        val rootStepLocations = ScriptConventions.orderedDirectChildLocations(
            graphNotation, AttributeLocation(scriptLocation, ScriptConventions.stepsAttributePath))

        val resultAnalysis = ScriptResultAnalysis.analyze(graphNotation, documentPath)

        // Which step values anything actually reads — one document-wide static scan per compile (so once per run,
        // and once per hosted-child document via ScriptRunContext's childLogics cache). Lets a collecting step skip
        // the work when nothing will look; see [ScriptValueReferences] for why it is compile-time and conservative.
        val valueReferencedSteps = ScriptValueReferences.analyze(
            documentPath, graphDefinition, graphInstance, rootStepLocations,
            resultAnalysis.implicitResultStep)

        val structure = ScriptRunStructure(
            scriptLocation, graphNotation, graphDefinition, graphInstance,
            scriptTree, scriptValidation, resultSignature, valueReferencedSteps, services)

        val parameters = ScriptConventions.orderedDirectChildLocations(
            graphNotation, AttributeLocation(scriptLocation, ScriptConventions.parametersAttributePath))
            .map { LogicParameter.of(it, graphNotation, services.objectStableMapper) }

        // The signature's inputs are the declared parameters (in order); a caller binding by signature — e.g. a
        // Flow logic-host vertex binding its wired inputs to the leading parameters — resolves the right name.
        // Types stay `any` (the binding is by name).
        val inputSignature = BindingSchema.of(parameters.map(LogicParameter::definition))
        val outputSignature = resultSignature

        return ScriptLogic(
            rootStepLocations, parameters, structure, LogicSignature(inputSignature, outputSignature))
    }
}
