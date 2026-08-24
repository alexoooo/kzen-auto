package tech.kzen.auto.server.objects.job

import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.job.JobChannelDerivation
import tech.kzen.auto.common.objects.document.job.JobChannelSynthesis
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.job.JobSignatureCapability
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.auto.common.objects.document.logic.StepValidation
import tech.kzen.auto.common.objects.document.logic.ValidationDigestEcho
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.server.objects.job.worker.WorkerBase
import tech.kzen.auto.server.objects.job.worker.WorkerLane
import tech.kzen.auto.server.objects.job.worker.WorkerLaneAttempt
import tech.kzen.auto.server.objects.job.worker.WorkerLaneContext
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionContext
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.LocalGraphStore


/**
 * The Job document's server-side validation pass (the [tech.kzen.auto.server.objects.script.ScriptValidator]
 * analogue): the STATIC PAYLOAD-TYPE WALK. Worker lanes ([WorkerLane]) are folded along the order-driven
 * wiring ([JobChannelDerivation] — the same shared derivation the client draws pipes from) in document order,
 * each Worker mapping its input lane to its output lane via the [WorkerBase.payloadFlow] capability — an
 * instance method, so no general layer learns a Worker type (the extension rule; a third-party Worker
 * participates by overriding it). Expression Workers compile-check their expressions where the static scope
 * is known, and parse them for syntax where it is not (a CSV lane), so a broken expression surfaces as a
 * validation error on the Worker's card instead of a run-time crash — and the inferred payload types flow
 * downstream (each expression compiles against the receiver type the walk gives it, both here and at run
 * time via [tech.kzen.auto.common.paradigm.job.control.JobControl.payloadType]).
 *
 * The walk needs live Worker instances, so [execute] synthesizes the channels and instantiates the Job graph
 * exactly as a run compile would (the ScriptValidator instantiate-to-validate precedent) — behind
 * [JobValidationCache], which also serves the run path ([tech.kzen.auto.server.exec.job.JobRun]), so editor
 * requests and run compiles share entries. A Worker missing from the instance graph (a pruned blank-reference
 * Worker, e.g. a RunWorker with no callee chosen) gets NO entry — pruned is not broken (it simply does not
 * run); the walk treats its lane as unknown.
 */
@Reflect
class JobValidator(
    @Service private val graphStore: LocalGraphStore,
    @Service private val environment: GraphEnvironment,
    @Service private val notationMetadataReader: NotationMetadataReader,
    @Service private val jobValidationCache: JobValidationCache
): DetachedAction {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun validate(
            documentPath: DocumentPath,
            graphDefinition: GraphDefinition,
            graphInstance: GraphInstance,
            environment: GraphEnvironment,
            definitionContext: WorkerDefinitionContext = WorkerDefinitionContext(
                graphDefinition, graphInstance, environment)
        ): JobValidation {
            val graphStructure = graphDefinition.graphStructure
            val graphNotation = graphStructure.graphNotation
            val documentNotation = graphNotation.documents[documentPath]
                ?: return JobValidation.empty
            if (!JobConventions.isJob(documentNotation)) {
                return JobValidation.empty
            }

            val jobMainLocation = documentPath.toObjectLocation(NotationConventions.mainObjectPath)
            val context = WorkerLaneContext(
                JobSignatureCapability.signature(graphStructure, jobMainLocation).inputs,
                graphStructure,
                ClassLoaderUtils.dynamicParentClassLoader())
            // The saved (pre-synthesis) structure drives the derivation — the same rule the client draws
            // pipes from — giving each downstream Worker its single inferred upstream.
            val upstreamByDownstream: Map<ObjectPath, ObjectPath> = JobChannelDerivation
                .derive(graphStructure, documentPath)
                .connections
                .associate { it.downstreamWorker.objectPath to it.upstreamWorker.objectPath }

            val workerPaths = documentNotation.directNestedObjectPaths(
                NotationConventions.mainObjectPath, JobConventions.workersAttributeName)

            val signature = JobSignatureCapability.signature(graphStructure, jobMainLocation)
            val resultErrors = resultYielderErrors(workerPaths, documentPath, graphStructure.graphNotation, signature)

            val outputLanes = mutableMapOf<ObjectPath, WorkerLane>()
            val workerValidations = mutableMapOf<ObjectPath, StepValidation>()

            for (workerPath in workerPaths) {
                val workerLocation = ObjectLocation(documentPath, workerPath)
                val worker = graphInstance[workerLocation]?.reference as? WorkerBase
                    ?: continue
                worker.loadDefinitionContext(definitionContext)

                val inputLane = upstreamByDownstream[workerPath]
                    ?.let { outputLanes[it] }
                    ?: WorkerLane.unknown

                // Defensive: a misbehaving payloadFlow (a third-party override throwing on a half-edited
                // config) degrades to an unknown lane with its message, never fails the whole pass.
                val attempt =
                    try {
                        worker.payloadFlow(inputLane, context)
                    }
                    catch (e: Exception) {
                        WorkerLaneAttempt(WorkerLane.unknown, e.message ?: e.toString())
                    }

                outputLanes[workerPath] = attempt.lane
                val joinedError = listOfNotNull(attempt.errorMessage, resultErrors[workerPath])
                    .distinct()
                    .joinToString("; ")
                    .ifBlank { null }
                workerValidations[workerPath] = StepValidation(
                    attempt.lane.payloadType, joinedError,
                    flatColumns = attempt.lane.flatColumns)
            }

            for ((path, error) in resultErrors) {
                workerValidations.putIfAbsent(path, StepValidation(null, error))
            }

            return JobValidation(workerValidations)
        }


        private fun resultYielderErrors(
            workerPaths: List<ObjectPath>,
            documentPath: DocumentPath,
            graphNotation: tech.kzen.lib.common.model.structure.notation.GraphNotation,
            signature: tech.kzen.lib.common.exec.engine.LogicSignature
        ): Map<ObjectPath, String> {
            val active = mutableListOf<Pair<ObjectPath, String>>()
            for (workerPath in workerPaths) {
                val location = ObjectLocation(documentPath, workerPath)
                if (!JobSignatureCapability.yieldsResult(graphNotation, location)) {
                    continue
                }
                val configured = graphNotation
                    .firstAttribute(location, JobConventions.resultAttributeName)
                    .asString()
                    .orEmpty()
                val component =
                    if (JobSignatureCapability.isResultSink(graphNotation, location)) {
                        configured.ifBlank { "main" }
                    }
                    else {
                        configured
                    }
                if (component.isNotBlank()) {
                    active.add(workerPath to component)
                }
            }

            val errors = linkedMapOf<ObjectPath, MutableList<String>>()
            for ((path, component) in active) {
                if (signature.outputs.find(tech.kzen.lib.common.exec.tuple.TupleComponentName(component)) == null) {
                    errors.getOrPut(path, ::mutableListOf)
                        .add("No result type declared in the Job signature for '$component'")
                }
            }
            for ((component, paths) in active.groupBy({ it.second }, { it.first })) {
                if (paths.size > 1) {
                    val message = "Multiple result yielders for '$component': " +
                        paths.joinToString { it.name.value }
                    for (path in paths) {
                        errors.getOrPut(path, ::mutableListOf).add(message)
                    }
                }
            }
            return errors.mapValues { it.value.joinToString("; ") }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(
        request: ExecutionRequest
    ): ExecutionResult {
        val documentPathValue = request.getSingle(CommonRestApi.paramHostDocumentPath)
            ?: return ExecutionResult.failure("Missing document path")

        val documentPath = DocumentPath.parse(documentPathValue)

        val graphDefinitionAttempt = graphStore.graphDefinition()
        val transitiveSuccessful = graphDefinitionAttempt.transitiveSuccessful

        // Resolved from the same snapshot the cache key and the compute use, so a cache hit echoes the digest
        // of the notation its entry was computed against.
        val documentNotation = graphDefinitionAttempt.graphStructure.graphNotation.documents[documentPath]
            ?: return ExecutionResult.failure("Document not found: $documentPath")

        // A cache hit skips channel synthesis, graph filtering and instantiation entirely (keyed on the FULL
        // definition — linked-callee edits must invalidate — matching the run path's key).
        val jobValidation = jobValidationCache.jobValidation(documentPath, transitiveSuccessful) {
            val synthesis = JobChannelSynthesis(notationMetadataReader)
                .synthesize(transitiveSuccessful, documentPath)
            val filteredDefinition = synthesis.graphDefinition.filterTransitive(documentPath)

            val graphInstance = GraphCreator.createGraph(filteredDefinition, environment)

            validate(
                documentPath,
                transitiveSuccessful,
                graphInstance,
                environment)
        }

        return ExecutionSuccess
            .ofValue(jobValidation.asExecutionValue())
            .withDetail(ValidationDigestEcho.detail(documentNotation))
    }
}
