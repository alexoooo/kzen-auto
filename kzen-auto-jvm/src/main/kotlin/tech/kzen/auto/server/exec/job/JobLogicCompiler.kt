package tech.kzen.auto.server.exec.job

import tech.kzen.auto.common.objects.document.job.JobChannelSynthesis
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.job.JobSignatureCapability
import tech.kzen.auto.common.objects.document.logic.ParameterDefaultDefiner
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions


/**
 * Translates a Job document's notation graph into a [JobLogic] runnable on the new engine — the Job analogue of
 * [tech.kzen.auto.server.exec.script.ScriptLogicCompiler] / [tech.kzen.auto.server.exec.flow.FlowLogicCompiler].
 *
 * The order-driven Channels are synthesized once here — the same [JobChannelSynthesis] augment-then-redefine the
 * old `JobExecution` ran each tick — so the run graph has real Channel objects
 * with the Worker ports filled even though the saved notation keeps them blank and omits Channel objects. The
 * resulting augmented + `filterTransitive`d definition, the Worker locations (document order, read from the
 * un-augmented notation since synthesis only adds channels), and the synthesized Channel locations are what
 * [JobRun] instantiates and hosts at run time.
 *
 * Because synthesized Channel identity is deterministic ([JobChannelSynthesis]), re-running this compile on a
 * live edit yields the same Channel [stable ids][tech.kzen.lib.common.service.store.normal.ObjectStableId] — so a
 * migrate ([tech.kzen.lib.server.exec.engine.RunEngine.migrate]) carries each channel's in-flight payloads across
 * the rebuild by stable id (see [JobRun]). The Logic signature is derived via
 * [tech.kzen.auto.common.objects.document.job.JobSignatureCapability] (inputs from the `parameters` branch of
 * typed ParameterBinding declarations, in document order; outputs from the document's declared `results`
 * signature map, Script parity) — the same notation-only derivation the client editors read, so the two sides
 * cannot drift; [JobRun] seeds the parameters (arguments falling back to declared defaults, see [JobParameters])
 * and harvests the results at run time.
 */
object JobLogicCompiler {
    fun compile(
        jobLocation: ObjectLocation,
        graphNotation: GraphNotation,
        graphDefinition: GraphDefinition,
        services: LogicCompilerServices
    ): JobLogic {
        val documentPath = jobLocation.documentPath

        val synthesis = JobChannelSynthesis(services.notationMetadataReader)
            .synthesize(graphDefinition, documentPath)
        val filteredDefinition = synthesis.graphDefinition.filterTransitive(documentPath)

        val documentNotation = graphNotation.documents[documentPath]
            ?: throw IllegalArgumentException("Job document not found: $documentPath")

        val workerLocations = documentNotation
            .directNestedObjectPaths(NotationConventions.mainObjectPath, JobConventions.workersAttributeName)
            .map { ObjectLocation(documentPath, it) }

        // Derived from the SAVED notation (the pre-synthesis structure, matching what the client sees):
        // inputs from the `parameters` declarations, outputs from the declared `results` signature map — not
        // the synthesized channels.
        val logicSignature = JobSignatureCapability.signature(graphDefinition.graphStructure, jobLocation)

        // Per-parameter typed defaults (the `default` scalar coerced by the declared `type`), the fallback
        // JobControl.parameter serves when the run binds no argument.
        val parameterDefaults = documentNotation
            .directNestedObjectPaths(NotationConventions.mainObjectPath, LogicConventions.parametersAttributeName)
            .associate { parameterPath ->
                val parameterLocation = ObjectLocation(documentPath, parameterPath)
                TupleComponentName(parameterPath.name.value) to
                        ParameterDefaultDefiner.resolve(parameterLocation, graphNotation)
            }

        return JobLogic(
            jobLocation,
            filteredDefinition,
            workerLocations,
            synthesis.channelLocations,
            logicSignature,
            JobParameters(logicSignature.inputs, parameterDefaults),
            graphNotation,
            graphDefinition,
            services)
    }
}
