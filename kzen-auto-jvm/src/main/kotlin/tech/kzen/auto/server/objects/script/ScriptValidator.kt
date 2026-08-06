package tech.kzen.auto.server.objects.script

import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.logic.ResultSignatureDefiner
import tech.kzen.auto.common.objects.document.logic.context.LogicContextAnalysis
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptResultAnalysis
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.common.objects.document.logic.StepValidation
import tech.kzen.auto.common.objects.document.logic.ValidationDigestEcho
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.server.objects.logic.TypeAssignability
import tech.kzen.auto.server.objects.registry.ObjectRegistryDocument
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.LocalGraphStore


@Reflect
class ScriptValidator(
    @Service private val graphStore: LocalGraphStore,
    @Service private val graphCreator: GraphCreator,
    @Service private val environment: GraphEnvironment,
    @Service private val scriptValidationCache: ScriptValidationCache,
    @Service private val cachedKotlinCompiler: CachedKotlinCompiler
): DetachedAction {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun validate(
            documentPath: DocumentPath,
            graphNotation: GraphNotation,
            graphDefinition: GraphDefinition,
            graphInstance: GraphInstance,
            cachedKotlinCompiler: CachedKotlinCompiler,
            scriptTree: ScriptTree = ScriptTree.read(documentPath, graphDefinition)
        ): ScriptValidation {
            val documentNotation = graphNotation.documents[documentPath]
                ?: throw IllegalArgumentException("Document not found: $documentPath")

            val objectRegistryScan = ObjectRegistryDocument.scan(graphNotation)

            val stepValidationBuffer = mutableMapOf<ObjectPath, StepValidation>()
            val resultSignature = ResultSignatureDefiner.parse(
                graphNotation.firstAttribute(
                    documentPath.toObjectLocation(NotationConventions.mainObjectPath),
                    ScriptConventions.resultsAttributePath))
            val scriptDefinitionContext = ScriptDefinitionContext(
                scriptTree,
                ScriptValidation(stepValidationBuffer),
                objectRegistryScan,
                resultSignature,
                graphNotation)

            val stepObjectLocations = documentNotation
                .objects
                .notations
                .map
                .keys
                .map { documentPath.toObjectLocation(it) }
                .filter { objectLocation ->
                    graphNotation
                        .inheritanceChain(objectLocation)
                        .any { it.objectPath.name == ScriptConventions.stepObjectName }
                }

            val remainingSteps = mutableSetOf<ObjectPath>()
            remainingSteps.addAll(stepObjectLocations.map { it.objectPath })

            while (remainingSteps.isNotEmpty()) {
                val nextValidations = validationIteration(
                    remainingSteps, scriptDefinitionContext, graphInstance, documentPath)

                if (nextValidations.isEmpty()) {
                    break
                }

                stepValidationBuffer.putAll(nextValidations)

                remainingSteps.removeAll(nextValidations.keys)
            }

            // A step still unresolved once the fixpoint stops making progress depends on a cycle or on an
            // object that never defines a type; give it an explicit entry so the editor shows the problem
            // rather than nothing.
            for (survivor in remainingSteps) {
                stepValidationBuffer.putIfAbsent(
                    survivor, StepValidation(null, "Unresolved: circular or unavailable dependency"))
            }

            // Context findings (logic-spec §6) merge in LAST — after the type fixpoint and after the survivor
            // pass — because the analysis reads notation only and depends on neither. Two cases, both real:
            // a step that already has an entry keeps its type and gains the finding; a step with NO entry (its
            // `definition()` returned null, so the iteration `continue`d past it) needs a fresh one. The
            // `?: StepValidation(null, null)` covers both without a branch.
            val contextFindings = LogicContextAnalysis.analyze(graphNotation, documentPath)

            // Errors are JOINED onto any compile error already present rather than replacing it: both are real
            // reasons the step cannot run, and the client lifts every non-null errorMessage into its Run gate.
            for ((objectPath, error) in contextFindings.errors) {
                val existing = stepValidationBuffer[objectPath] ?: StepValidation(null, null)
                stepValidationBuffer[objectPath] = existing.copy(
                    errorMessage = listOfNotNull(existing.errorMessage, error).joinToString(" "))
            }

            for ((objectPath, warning) in contextFindings.warnings) {
                stepValidationBuffer[objectPath] =
                    (stepValidationBuffer[objectPath] ?: StepValidation(null, null))
                        .copy(warningMessage = warning)
            }

            // Reads the types the fixpoint resolved, so it merges after it. Both channels JOIN: a step can
            // carry a context finding and a result finding at once, and neither may swallow the other.
            val resultFindings = resultFindings(
                documentPath, graphNotation, resultSignature, stepValidationBuffer, cachedKotlinCompiler)

            for ((objectPath, finding) in resultFindings) {
                val existing = stepValidationBuffer[objectPath] ?: StepValidation(null, null)
                stepValidationBuffer[objectPath] = existing.copy(
                    errorMessage = listOfNotNull(existing.errorMessage, finding.error)
                        .joinToString(" ").takeIf { it.isNotEmpty() },
                    warningMessage = listOfNotNull(existing.warningMessage, finding.warning)
                        .joinToString(" ").takeIf { it.isNotEmpty() })
            }

            return ScriptValidation(stepValidationBuffer)
        }


        private data class ResultFinding(
            val error: String? = null,
            val warning: String? = null)


        /**
         * How the Script's declared result is (or is not) delivered: a root step placed after a Result step
         * never runs, and — when no Result step supplies the value — the Script's terminal root step must
         * produce a value assignable to the declared type.
         *
         * A void Script (no declared main result) has no contract to check, so it gets no findings at all:
         * ending on a step that produces nothing is exactly what it is for.
         */
        private fun resultFindings(
            documentPath: DocumentPath,
            graphNotation: GraphNotation,
            resultSignature: TupleDefinition,
            stepValidations: Map<ObjectPath, StepValidation>,
            cachedKotlinCompiler: CachedKotlinCompiler
        ): Map<ObjectPath, ResultFinding> {
            val analysis = ScriptResultAnalysis.analyze(graphNotation, documentPath)
            val findings = mutableMapOf<ObjectPath, ResultFinding>()

            // Advisory: an unreachable step is a dead branch of the author's intent, not a reason to block Run.
            for (unreachable in analysis.unreachableRootSteps) {
                findings[unreachable.objectPath] = ResultFinding(
                    warning = "Never runs — the Result step above ends the Script.")
            }

            if (!analysis.declaresMainResult) {
                return findings
            }

            val declaredType = resultSignature.find(TupleComponentName.main)!!.metadata

            if (analysis.rootSteps.isEmpty()) {
                findings[NotationConventions.mainObjectPath] = ResultFinding(
                    error = "Result declares ${declaredType.toSimple()} but this Script has no steps.")
                return findings
            }

            val implicitResultStep = analysis.implicitResultStep
                ?: return findings

            // A validated step with no value is Unit, mirroring ForEachStep.bodyTerminalType. Every step has an
            // entry by now, so the fallback only backstops a row that already carries its own error.
            val actualType = stepValidations[implicitResultStep.objectPath]?.typeMetadata ?: TypeMetadata.unit

            // Unit has its own message rather than falling through to the mismatch one: it IS assignable to
            // Any, and "Unit is not assignable" reads as noise to an author who ended on a Display step.
            val error =
                when {
                    actualType == TypeMetadata.unit ->
                        "Result declares ${declaredType.toSimple()} but this step produces no value — end " +
                                "with a step that produces one, or add a Result step."

                    !TypeAssignability.isAssignable(
                            actualType, declaredType, cachedKotlinCompiler,
                            ClassLoaderUtils.dynamicParentClassLoader()) ->
                        "Result declares ${declaredType.toSimple()} but this step produces " +
                                actualType.toSimple()

                    else ->
                        null
                }

            if (error != null) {
                findings[implicitResultStep.objectPath] = ResultFinding(error = error)
            }

            return findings
        }


        private fun validationIteration(
            remainingObjectPaths: Collection<ObjectPath>,
            scriptDefinitionContext: ScriptDefinitionContext,
            graphInstance: GraphInstance,
            documentPath: DocumentPath
        ):
            Map<ObjectPath, StepValidation>
        {
            val builder = mutableMapOf<ObjectPath, StepValidation>()
            for (objectPath in remainingObjectPaths) {
                val stepObjectLocation = documentPath.toObjectLocation(objectPath)
                val instance = graphInstance.objectInstances[stepObjectLocation]?.reference as? ScriptStep
                if (instance == null) {
                    builder[objectPath] = StepValidation(
                        null, "Not found")
                }
                else {
                    val valueDefinition = instance.definition(scriptDefinitionContext)
                        ?: continue

                    val returnValueDefinition = valueDefinition.returnValueDefinition
                    val typeMetadata =
                        if (returnValueDefinition == null) {
                            null
                        }
                        else {
                            returnValueDefinition
                                .find(TupleComponentName.main)
                                ?.metadata
                                ?: TypeMetadata.unit
                        }

                    builder[objectPath] = StepValidation(
                        typeMetadata, valueDefinition.validationError)
                }
            }
            return builder
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

        // Resolved from the same snapshot the cache key and the compute use, so a cache hit echoes the digest
        // of the notation its entry was computed against.
        val documentNotation = graphDefinitionAttempt.graphStructure.graphNotation.documents[documentPath]
            ?: return ExecutionResult.failure("Document not found: $documentPath")

        // A cache hit skips graph filtering and instantiation entirely (keyed on the FULL definition:
        // linked-callee and registry edits must invalidate, and the key must match the run-compile path's).
        val scriptValidation = scriptValidationCache.scriptValidation(
            documentPath, graphDefinitionAttempt.transitiveSuccessful
        ) {
            val stepGraphDefinition = graphDefinitionAttempt
                .transitiveSuccessful
                .filterTransitive(documentPath)

            val graphInstance = graphCreator
                .createGraph(stepGraphDefinition, environment)

            validate(
                documentPath,
                graphDefinitionAttempt.graphStructure.graphNotation,
                stepGraphDefinition,
                graphInstance,
                cachedKotlinCompiler)
        }

        return ExecutionSuccess
            .ofValue(scriptValidation.asExecutionValue())
            .withDetail(ValidationDigestEcho.detail(documentNotation))
    }
}