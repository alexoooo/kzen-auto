package tech.kzen.auto.common.objects.document.job

import tech.kzen.auto.common.objects.document.logic.ResultSignatureDefiner
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
 * its declared `type` (TypeMetadata; absent/unparseable falls back to Any). OUTPUTS come from the Job document's
 * declared `results` signature map (Script parity, parsed by [ResultSignatureDefiner]) — plain data on the main
 * object, independent of which ResultSink Workers exist; each sink yields INTO a declared component by its
 * `result` name (blank = main), validated by [tech.kzen.auto.server.objects.job.worker.ResultSinkWorker]'s
 * payloadFlow. Reads saved notation only. [isResultSink] classifies sink Workers capability-based, never
 * class-based (CC-17): membership of the [JobConventions.resultSinkObjectName] marker in the inheritance chain.
 */
object JobSignatureCapability {
    private val typeAttributePath = AttributePath.ofName(AttributeName("type"))


    fun isResultSink(graphNotation: GraphNotation, workerLocation: ObjectLocation): Boolean {
        return hasMarker(graphNotation, workerLocation, JobConventions.resultSinkObjectName)
    }


    fun isResultYielder(graphNotation: GraphNotation, workerLocation: ObjectLocation): Boolean {
        return hasMarker(graphNotation, workerLocation, JobConventions.resultYielderObjectName)
    }


    fun yieldsResult(graphNotation: GraphNotation, workerLocation: ObjectLocation): Boolean {
        return isResultYielder(graphNotation, workerLocation)
    }


    private fun hasMarker(
        graphNotation: GraphNotation,
        workerLocation: ObjectLocation,
        marker: tech.kzen.lib.common.model.obj.ObjectName
    ): Boolean {
        if (workerLocation !in graphNotation.coalesce) {
            // Stale-location guard: a client observer can fire with a just-deleted / renamed callee, on which
            // inheritanceChain would throw.
            return false
        }

        return graphNotation.inheritanceChain(workerLocation).any {
            it.objectPath.name == marker
        }
    }


    fun signature(graphStructure: GraphStructure, jobMainLocation: ObjectLocation): LogicSignature {
        val graphNotation = graphStructure.graphNotation
        val documentNotation = graphNotation.documents[jobMainLocation.documentPath]
            ?: return LogicSignature.empty
        if (!JobConventions.isJob(documentNotation)) {
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

        val outputs = ResultSignatureDefiner.parse(
            graphNotation.firstAttribute(jobMainLocation, LogicConventions.resultsAttributePath))

        return LogicSignature(TupleDefinition(inputs), outputs)
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
}
