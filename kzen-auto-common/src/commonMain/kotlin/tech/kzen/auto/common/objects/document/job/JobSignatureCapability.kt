package tech.kzen.auto.common.objects.document.job

import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentDefinition
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions


/**
 * Derives a Job document's Logic signature from its signature-marker Workers — the single source shared by
 * [tech.kzen.auto.server.exec.job.JobLogicCompiler] (server) and the callee-parameter editors (client), so the two
 * cannot drift (the [FlowConventions] precedent). Classification is capability-based, never class-based (CC-17): a
 * Worker whose inheritance chain reaches the [JobConventions.parameterSourceObjectName] marker declares one input
 * parameter (its `parameter` attribute names it; its output port's `of:` element type types it — for a Collection
 * argument the type describes the ELEMENT), one reaching [JobConventions.resultSinkObjectName] declares one output
 * component (`result`, blank = main; typed by its input port's `of:`). Reads saved notation + metadata only
 * (blank/open ports are fine — no synthesis, no instances). Document order is signature order; a blank parameter
 * name is filtered (Flow parity); a blank result name maps to "main" (the hosts' single-positional harvest
 * convention).
 */
object JobSignatureCapability {
    enum class Role { Parameter, Result }


    fun roleOf(graphNotation: GraphNotation, workerLocation: ObjectLocation): Role? {
        if (workerLocation !in graphNotation.coalesce) {
            // Stale-location guard: a client observer can fire with a just-deleted / renamed callee, on which
            // inheritanceChain would throw.
            return null
        }

        val chainNames = graphNotation.inheritanceChain(workerLocation)
            .mapTo(mutableSetOf()) { it.objectPath.name }

        return when {
            JobConventions.parameterSourceObjectName in chainNames -> Role.Parameter
            JobConventions.resultSinkObjectName in chainNames -> Role.Result
            else -> null
        }
    }


    fun signature(graphStructure: GraphStructure, jobMainLocation: ObjectLocation): LogicSignature {
        val graphNotation = graphStructure.graphNotation
        val documentNotation = graphNotation.documents[jobMainLocation.documentPath]
            ?: return LogicSignature.empty
        if (! JobConventions.isJob(documentNotation)) {
            return LogicSignature.empty
        }

        val inputs = mutableListOf<TupleComponentDefinition>()
        val outputs = mutableListOf<TupleComponentDefinition>()

        val workerPaths = documentNotation.directNestedObjectPaths(
            NotationConventions.mainObjectPath, JobConventions.workersAttributeName)
        for (workerPath in workerPaths) {
            val workerLocation = ObjectLocation(jobMainLocation.documentPath, workerPath)
            when (roleOf(graphNotation, workerLocation)) {
                Role.Parameter -> {
                    val name = graphNotation.firstAttribute(
                        workerLocation, JobConventions.parameterAttributePath)?.asString() ?: ""
                    if (name.isNotEmpty()) {
                        inputs.add(TupleComponentDefinition(
                            TupleComponentName(name),
                            portElementType(graphStructure, workerLocation, JobChannelPorts.Kind.Output)))
                    }
                }

                Role.Result -> {
                    val name = graphNotation.firstAttribute(
                        workerLocation, JobConventions.resultAttributePath)?.asString() ?: ""
                    val componentName =
                        if (name.isEmpty()) { TupleComponentName.main } else { TupleComponentName(name) }
                    outputs.add(TupleComponentDefinition(
                        componentName,
                        portElementType(graphStructure, workerLocation, JobChannelPorts.Kind.Input)))
                }

                null -> {}
            }
        }

        return LogicSignature(TupleDefinition(inputs), TupleDefinition(outputs))
    }


    // First port of the given [kind] whose metadata declares an `of:` element type; LogicType.any otherwise.
    private fun portElementType(
        graphStructure: GraphStructure,
        workerLocation: ObjectLocation,
        kind: JobChannelPorts.Kind
    ): LogicType {
        val objectMetadata = graphStructure.graphMetadata.get(workerLocation)
            ?: return LogicType.any

        for ((_, attributeMetadata) in objectMetadata.attributes.map) {
            if (JobChannelPorts.kindOf(attributeMetadata.type) != kind) {
                continue
            }
            val elementType = attributeMetadata.type?.generics?.getOrNull(0)
                ?: continue
            return LogicType(elementType)
        }

        return LogicType.any
    }
}
