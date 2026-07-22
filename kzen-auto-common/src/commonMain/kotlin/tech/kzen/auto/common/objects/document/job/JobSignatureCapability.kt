package tech.kzen.auto.common.objects.document.job

import tech.kzen.auto.common.objects.document.logic.TypeMetadataDefiner
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentDefinition
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions


/**
 * Derives a Job document's Logic signature — the single source shared by
 * [tech.kzen.auto.server.exec.job.JobLogicCompiler] (server) and the callee-parameter editors (client), so the two
 * cannot drift (the [FlowConventions] precedent). INPUTS come from the Job's `parameters` branch of typed
 * ParameterBinding declarations (Script parity): component name = the binding object's own name, component type =
 * its declared `type` (TypeMetadata; absent/unparseable falls back to Any). OUTPUTS come from the ResultSink
 * signature-marker Workers, classified capability-based, never class-based (CC-17): a Worker whose inheritance
 * chain reaches the [JobConventions.resultSinkObjectName] marker declares one output component (`result`, blank =
 * main; typed by its input port's `of:`). Reads saved notation + metadata only (blank/open ports are fine — no
 * synthesis, no instances). Document order is signature order; a blank result name maps to "main" (the hosts'
 * single-positional harvest convention).
 */
object JobSignatureCapability {
    private val typeAttributePath = AttributePath.ofName(AttributeName("type"))


    fun isResultSink(graphNotation: GraphNotation, workerLocation: ObjectLocation): Boolean {
        if (workerLocation !in graphNotation.coalesce) {
            // Stale-location guard: a client observer can fire with a just-deleted / renamed callee, on which
            // inheritanceChain would throw.
            return false
        }

        return graphNotation.inheritanceChain(workerLocation).any {
            it.objectPath.name == JobConventions.resultSinkObjectName
        }
    }


    fun signature(graphStructure: GraphStructure, jobMainLocation: ObjectLocation): LogicSignature {
        val graphNotation = graphStructure.graphNotation
        val documentNotation = graphNotation.documents[jobMainLocation.documentPath]
            ?: return LogicSignature.empty
        if (! JobConventions.isJob(documentNotation)) {
            return LogicSignature.empty
        }

        val inputs = documentNotation
            .directNestedObjectPaths(NotationConventions.mainObjectPath, LogicConventions.parametersAttributeName)
            .map { parameterPath ->
                val parameterLocation = ObjectLocation(jobMainLocation.documentPath, parameterPath)
                TupleComponentDefinition(
                    TupleComponentName(parameterPath.name.value),
                    declaredType(graphNotation, parameterLocation))
            }

        val outputs = mutableListOf<TupleComponentDefinition>()

        val workerPaths = documentNotation.directNestedObjectPaths(
            NotationConventions.mainObjectPath, JobConventions.workersAttributeName)
        for (workerPath in workerPaths) {
            val workerLocation = ObjectLocation(jobMainLocation.documentPath, workerPath)
            if (! isResultSink(graphNotation, workerLocation)) {
                continue
            }

            val name = graphNotation.firstAttribute(
                workerLocation, JobConventions.resultAttributePath)?.asString() ?: ""
            val componentName =
                if (name.isEmpty()) { TupleComponentName.main } else { TupleComponentName(name) }
            outputs.add(TupleComponentDefinition(
                componentName,
                portElementType(graphStructure, workerLocation, JobChannelPorts.Kind.Input)))
        }

        return LogicSignature(TupleDefinition(inputs), TupleDefinition(outputs))
    }


    // A ParameterBinding declaration's `type` as a LogicType; LogicType.any when absent / unparseable
    // (the archetype defaults the attribute to kotlin.Any anyway).
    private fun declaredType(graphNotation: GraphNotation, parameterLocation: ObjectLocation): LogicType {
        val typeMetadata = graphNotation
            .firstAttribute(parameterLocation, typeAttributePath)
            ?.let { TypeMetadataDefiner.parse(it) }
            ?: return LogicType.any
        return LogicType(typeMetadata)
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
