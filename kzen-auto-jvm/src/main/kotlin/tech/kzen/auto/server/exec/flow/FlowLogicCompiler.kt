package tech.kzen.auto.server.exec.flow

import tech.kzen.auto.common.objects.document.flow.FlowConventions
import tech.kzen.auto.common.objects.document.flow.FlowStructureValidator
import tech.kzen.auto.common.paradigm.flow.api.FlowLogicHost
import tech.kzen.auto.common.paradigm.flow.api.FlowRunInput
import tech.kzen.auto.common.paradigm.flow.api.FlowRunOutput
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix
import tech.kzen.auto.server.exec.LogicCompiler
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.binding.DataPresence
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.engine.LogicFailure
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.store.normal.ObjectStableId


/**
 * Translates a Flow document's notation graph into a [FlowLogic] runnable on the new engine — the Flow
 * analogue of [tech.kzen.auto.server.exec.script.ScriptLogicCompiler]. It reads the same `vertices` / `edges`
 * dataflow shape through [FlowMatrix] that the executor uses; the per-vertex mechanics are deferred to
 * [FlowRun], which builds one instance per run and drives every vertex execution from it.
 *
 * The two structural derivations done here once (rather than per run): the Logic signature (via
 * [FlowConventions] — the same notation-only derivation the client editors read, so the two sides
 * can't drift) and the pre-compiled [FlowLogicHost] callees (each compiled through [LogicCompiler],
 * so a Flow can host a Script or another Flow).
 *
 * Host discovery walks the instantiated graph because the capability is a Kotlin interface, and only an
 * instance can answer whether a class implements one — the same `is FlowLogicHost` test [FlowRun] applies at
 * run time, so compiler and runner cannot disagree. A notation marker would be a second source of truth a
 * third party could get half-right.
 */
object FlowLogicCompiler {
    fun compile(
        flowLocation: ObjectLocation,
        graphNotation: GraphNotation,
        graphDefinition: GraphDefinition,
        services: LogicCompilerServices
    ): FlowLogic {
        val documentPath = flowLocation.documentPath
        val matrix = FlowMatrix.ofDocument(documentPath, graphDefinition.graphStructure)

        // Refuse to compile a structurally broken flow: without this the first symptom is a
        // stalled run or a mid-run input check. The client renders the same findings in a banner
        // (FlowController), so by the time this throws the user has already seen them.
        val structureFindings = FlowStructureValidator.validate(flowLocation, graphNotation, matrix)
        if (structureFindings.isNotEmpty()) {
            throw LogicFailure("Flow structure invalid: ${structureFindings.joinToString("; ")}")
        }

        val graphInstance = GraphCreator.createGraph(
            graphDefinition.filterTransitive(documentPath), services.graphEnvironment)

        val inputs = FlowConventions
            .inputParameterNames(graphNotation, flowLocation)
            .map { dynamicBinding(it, DataPresence.Optional) }
            .toMutableList()
        val outputs = FlowConventions
            .outputResultNames(graphNotation, flowLocation)
            .map { dynamicBinding(it, DataPresence.Required) }
            .toMutableList()

        val childLogics = mutableMapOf<ObjectStableId, FlowChildLogic>()

        val orderedVertexLocations = matrix.verticesByLocation.values
            .sortedBy { it.indexInContainer }
            .map { it.objectLocation }

        for (vertexLocation in orderedVertexLocations) {
            val reference = graphInstance[vertexLocation]?.reference

            if (reference is FlowRunInput && inputs.none { it.name == reference.bindingName }) {
                inputs.add(dynamicBinding(reference.bindingName.value, DataPresence.Optional))
            }
            if (reference is FlowRunOutput && outputs.none { it.name == reference.bindingName }) {
                outputs.add(dynamicBinding(reference.bindingName.value, DataPresence.Required))
            }
            if (reference !is FlowLogicHost) {
                continue
            }

            val childLogic = LogicCompiler.compile(
                reference.instructions, graphNotation, graphDefinition, services)

            val childSignature = (childLogic as? Logic)?.signature()
                ?: throw LogicFailure("Child Logic is not binding-native: ${reference.instructions}")
            val parameterNames = childSignature.inputs.definitions.map { it.name }
            validateArguments(vertexLocation, reference, parameterNames, matrix)

            childLogics[services.objectStableMapper.objectStableId(vertexLocation)] = FlowChildLogic(
                services.objectStableMapper.objectStableId(reference.instructions),
                childLogic,
                childSignature.inputs)
        }

        return FlowLogic(
            documentPath,
            graphDefinition,
            childLogics,
            LogicSignature(BindingSchema.of(inputs), BindingSchema.of(outputs)),
            services.objectStableMapper,
            services.graphEnvironment)
    }


    /**
     * Refuse an `arguments` key that names no parameter of the callee, or one the vertex's wired inputs
     * already bind by position: either way the literal would silently not arrive where the author meant it,
     * and a run would surface that only as a wrong result.
     */
    private fun validateArguments(
        vertexLocation: ObjectLocation,
        host: FlowLogicHost,
        parameterNames: List<BindingName>,
        matrix: FlowMatrix
    ) {
        if (host.arguments.isEmpty()) {
            return
        }

        val vertexName = vertexLocation.objectPath.name.value
        val positionallyBound = parameterNames
            .take(wiredInputCount(vertexLocation, matrix))
            .toSet()

        for (argumentName in host.arguments.keys) {
            val parameterName = BindingName(argumentName)

            if (parameterName !in parameterNames) {
                throw LogicFailure(
                    "Argument '$argumentName' of '$vertexName' does not match a parameter of " +
                            "${host.instructions.asString()}: ${parameterNames.map { it.value }}")
            }

            if (parameterName in positionallyBound) {
                throw LogicFailure(
                    "Argument '$argumentName' of '$vertexName' conflicts with a wired input: " +
                            "the parameter is already bound by position")
            }
        }
    }


    private fun wiredInputCount(vertexLocation: ObjectLocation, matrix: FlowMatrix): Int {
        val vertexDescriptor = matrix.verticesByLocation[vertexLocation]
            ?: return 0

        return vertexDescriptor.inputNames.count {
            matrix.traceVertexBackFrom(vertexDescriptor, it) != null
        }
    }


    private fun dynamicBinding(name: String, presence: DataPresence): BindingDefinition =
        BindingDefinition(
            BindingName(name),
            DataContract(DataType.Dynamic(nullable = true)),
            presence)
}
