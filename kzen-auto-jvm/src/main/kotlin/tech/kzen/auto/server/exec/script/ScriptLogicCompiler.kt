package tech.kzen.auto.server.exec.script

import tech.kzen.auto.common.objects.document.script.ParameterDefaultDefiner
import tech.kzen.auto.common.objects.document.script.ResultSignatureDefiner
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.TypeMetadataDefiner
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.objects.script.ScriptValidator
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentDefinition
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
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
    private val defaultAttributePath = AttributePath.ofName(AttributeName("default"))
    private val typeAttributePath = AttributePath.ofName(AttributeName("type"))


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
        val scriptValidation = ScriptValidator.validate(
            documentPath, graphNotation, graphDefinition, graphInstance)
        val resultSignature = ResultSignatureDefiner.parse(
            graphNotation.firstAttribute(scriptLocation, ScriptConventions.resultsAttributePath))

        val structure = ScriptRunStructure(
            scriptLocation, graphNotation, graphDefinition, graphInstance,
            scriptTree, scriptValidation, resultSignature, services)

        val rootStepLocations = ScriptConventions.orderedDirectChildLocations(
            graphNotation, AttributeLocation(scriptLocation, ScriptConventions.stepsAttributePath))

        val parameters = ScriptConventions.orderedDirectChildLocations(
            graphNotation, AttributeLocation(scriptLocation, ScriptConventions.parametersAttributePath))
            .map { parameter(it, graphNotation, services) }

        // The signature's inputs are the declared parameters (in order); a caller binding by signature — e.g. a
        // Flow RunLogicVertex passing its single upstream message to the callee's first parameter — resolves the
        // right name. Types stay `any` (the binding is by name).
        val inputSignature = TupleDefinition(
            parameters.map { TupleComponentDefinition(it.name, LogicType.any) })

        return ScriptLogic(
            rootStepLocations, parameters, structure, LogicSignature(inputSignature, resultSignature))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parameter(
        location: ObjectLocation,
        graphNotation: GraphNotation,
        services: LogicCompilerServices
    ): ScriptParameter {
        return ScriptParameter(
            services.objectStableMapper.objectStableId(location),
            TupleComponentName(location.objectPath.name.value),
            parameterDefault(location, graphNotation))
    }


    private fun parameterDefault(location: ObjectLocation, graphNotation: GraphNotation): Any? {
        val defaultText = (graphNotation.firstAttribute(location, defaultAttributePath)
            as? ScalarAttributeNotation)
            ?.value
            ?: return null
        val type = graphNotation.firstAttribute(location, typeAttributePath)
            ?.let { TypeMetadataDefiner.parse(it) }
            ?: return null
        return ParameterDefaultDefiner.coerce(defaultText, type)
    }
}
