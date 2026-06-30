package tech.kzen.auto.server.exec.script

import tech.kzen.auto.common.objects.document.script.ParameterDefaultDefiner
import tech.kzen.auto.common.objects.document.script.ResultSignatureDefiner
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.TypeMetadataDefiner
import tech.kzen.auto.common.objects.document.script.model.RunStepInstructions
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.server.exec.script.step.DoWhileStep
import tech.kzen.auto.server.exec.script.step.ForEachStep
import tech.kzen.auto.server.exec.script.step.FormulaStep
import tech.kzen.auto.server.exec.script.step.IfStep
import tech.kzen.auto.server.exec.script.step.PauseStep
import tech.kzen.auto.server.exec.script.step.ResultStep
import tech.kzen.auto.server.exec.script.step.RunStep
import tech.kzen.auto.server.exec.script.step.SequenceStep
import tech.kzen.auto.server.exec.script.step.WaitStep
import tech.kzen.auto.server.objects.script.ScriptValidator
import tech.kzen.auto.server.objects.script.step.eval.StepExpressionSupport
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.platform.ClassNames

// The notation-side (document) step implementations, referenced only for type dispatch — the new engine-side
// step classes of the same name (above) are what this compiler produces.
import tech.kzen.auto.server.objects.script.step.control.DoWhileStep as DocDoWhileStep
import tech.kzen.auto.server.objects.script.step.control.IfStep as DocIfStep
import tech.kzen.auto.server.objects.script.step.control.PauseStep as DocPauseStep
import tech.kzen.auto.server.objects.script.step.control.WaitStep as DocWaitStep
import tech.kzen.auto.server.objects.script.step.control.foreach.ForEachStep as DocForEachStep
import tech.kzen.auto.server.objects.script.step.eval.FormulaStep as DocFormulaStep
import tech.kzen.auto.server.objects.script.step.control.RunStep as DocRunStep
import tech.kzen.auto.server.objects.script.step.eval.ResultStep as DocResultStep
import tech.kzen.auto.server.objects.script.step.value.BooleanLiteralStep as DocBooleanLiteralStep
import tech.kzen.auto.server.objects.script.step.value.NumberLiteralStep as DocNumberLiteralStep
import tech.kzen.auto.server.objects.script.step.value.NumberRangeStep as DocNumberRangeStep
import tech.kzen.auto.server.objects.script.step.value.TextLiteralStep as DocTextLiteralStep


/**
 * Translates a Script document's notation graph into a [ScriptLogic] (a [SequenceStep] tree) runnable on the
 * new engine. It reuses the existing definition/validation/compilation wholesale: the graph is instantiated
 * and validated exactly as [tech.kzen.auto.server.objects.script.ScriptExecution] does, and Formula/Result/
 * DoWhile expressions are evaluated through [StepExpressionSupport.evaluate] — the only difference is that
 * values are resolved from the engine's [ScriptRunContext] (by stable id) instead of ScriptExecutionContext.
 *
 * Structure (step order, branch / loop-body nesting) comes from [ScriptConventions.orderedDirectChildLocations],
 * the same document-position source the executor uses. Step types not yet ported throw [NotImplementedError].
 */
object ScriptLogicCompiler {
    //-----------------------------------------------------------------------------------------------------------------
    private val codeAttributePath = AttributePath.ofName(AttributeName("code"))
    private val conditionAttributePath = AttributePath.ofName(AttributeName("condition"))
    private val thenAttributePath = AttributePath.ofName(AttributeName("then"))
    private val elseAttributePath = AttributePath.ofName(AttributeName("else"))
    private val valueAttributePath = AttributePath.ofName(AttributeName("value"))
    private val millisecondsAttributePath = AttributePath.ofName(AttributeName("milliseconds"))
    private val fromAttributePath = AttributePath.ofName(AttributeName("from"))
    private val toAttributePath = AttributePath.ofName(AttributeName("to"))
    private val defaultAttributePath = AttributePath.ofName(AttributeName("default"))
    private val typeAttributePath = AttributePath.ofName(AttributeName("type"))
    private val argumentsAttributePath = AttributePath.ofName(AttributeName("arguments"))


    //-----------------------------------------------------------------------------------------------------------------
    fun compile(
        scriptLocation: ObjectLocation,
        graphNotation: GraphNotation,
        graphDefinition: GraphDefinition,
        graphEnvironment: GraphEnvironment,
        objectStableMapper: ObjectStableMapper,
        cachedKotlinCompiler: CachedKotlinCompiler
    ): ScriptLogic {
        val documentPath = scriptLocation.documentPath

        val graphInstance = GraphCreator.createGraph(
            graphDefinition.filterTransitive(documentPath), graphEnvironment)
        val scriptTree = ScriptTree.read(documentPath, graphDefinition)
        val scriptValidation = ScriptValidator.validate(
            documentPath, graphNotation, graphDefinition, graphInstance)
        val resultSignature = ResultSignatureDefiner.parse(
            graphNotation.firstAttribute(scriptLocation, ScriptConventions.resultsAttributePath))

        val translation = Translation(
            graphNotation, graphDefinition, graphEnvironment, graphInstance, scriptTree,
            scriptValidation, resultSignature, objectStableMapper, cachedKotlinCompiler)

        val parameters = ScriptConventions.orderedDirectChildLocations(
            graphNotation, AttributeLocation(scriptLocation, ScriptConventions.parametersAttributePath))
            .map { translation.parameter(it) }

        val root = translation.translateSequence(
            AttributeLocation(scriptLocation, ScriptConventions.stepsAttributePath))

        return ScriptLogic(root, parameters, LogicSignature(TupleDefinition.empty, resultSignature))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private class Translation(
        private val graphNotation: GraphNotation,
        private val graphDefinition: GraphDefinition,
        private val graphEnvironment: GraphEnvironment,
        private val graphInstance: GraphInstance,
        private val scriptTree: ScriptTree,
        private val scriptValidation: ScriptValidation,
        private val resultSignature: TupleDefinition,
        private val objectStableMapper: ObjectStableMapper,
        private val cachedKotlinCompiler: CachedKotlinCompiler
    ) {
        fun translateSequence(attributeLocation: AttributeLocation): SequenceStep {
            val childLocations = ScriptConventions.orderedDirectChildLocations(graphNotation, attributeLocation)
            return SequenceStep(childLocations.map { translateStep(it) })
        }


        fun parameter(location: ObjectLocation): ScriptParameter {
            return ScriptParameter(
                objectStableMapper.objectStableId(location),
                TupleComponentName(location.objectPath.name.value),
                parameterDefault(location))
        }


        private fun translateStep(location: ObjectLocation): ScriptStepLogic {
            return when (graphInstance[location]?.reference) {
                is DocFormulaStep -> formulaStep(location)
                is DocResultStep -> resultStep(location)
                is DocIfStep -> ifStep(location)
                is DocForEachStep -> forEachStep(location)
                is DocRunStep -> runStep(location)
                is DocDoWhileStep -> doWhileStep(location)
                is DocWaitStep -> WaitStep(
                    objectStableMapper.objectStableId(location),
                    longAttribute(location, millisecondsAttributePath))
                is DocPauseStep -> PauseStep(objectStableMapper.objectStableId(location))
                is DocTextLiteralStep -> literalStep(location, stringAttribute(location, valueAttributePath))
                is DocBooleanLiteralStep -> literalStep(location, booleanAttribute(location, valueAttributePath))
                is DocNumberLiteralStep -> literalStep(location, doubleAttribute(location, valueAttributePath))
                is DocNumberRangeStep -> literalStep(location, rangeValue(location))
                else -> throw NotImplementedError(
                    "Step type not yet supported in engine translation: $location")
            }
        }


        private fun formulaStep(location: ObjectLocation): FormulaStep {
            val code = stringAttribute(location, codeAttributePath)
            val nonUnitTypes = nonUnitTypesOf(location)
            return FormulaStep(objectStableMapper.objectStableId(location)) { runContext ->
                StepExpressionSupport.evaluate(
                    location, "Any?", code, nonUnitTypes, resolver(runContext), cachedKotlinCompiler)
            }
        }


        private fun resultStep(location: ObjectLocation): ResultStep {
            val code = stringAttribute(location, codeAttributePath)
            val nonUnitTypes = nonUnitTypesOf(location)
            val declaredType = resultSignature.find(TupleComponentName.main)?.metadata
                ?: error("Result step with no declared result type: $location")
            return ResultStep(objectStableMapper.objectStableId(location)) { runContext ->
                StepExpressionSupport.evaluate(
                    location, declaredType.toSimple(), code, nonUnitTypes,
                    resolver(runContext), cachedKotlinCompiler)
            }
        }


        private fun ifStep(location: ObjectLocation): IfStep {
            val conditionLocation = referenceLocation(location, conditionAttributePath)
                ?: error("If step has no condition reference: $location")
            return IfStep(
                objectStableMapper.objectStableId(location),
                objectStableMapper.objectStableId(conditionLocation),
                translateSequence(AttributeLocation(location, thenAttributePath)),
                translateSequence(AttributeLocation(location, elseAttributePath)))
        }


        private fun forEachStep(location: ObjectLocation): ForEachStep {
            val itemsLocation = referenceLocation(location, ScriptConventions.itemsAttributePath)
                ?: error("ForEach step has no items reference: $location")

            val itemBindingLocation = ScriptConventions.orderedDirectChildLocations(
                graphNotation, AttributeLocation(location, ScriptConventions.itemAttributePath))
                .singleOrNull()
            val itemBindingId = itemBindingLocation
                ?.let { objectStableMapper.objectStableId(it) }
                ?: error("ForEach step has no item binding: $location")

            return ForEachStep(
                objectStableMapper.objectStableId(location),
                objectStableMapper.objectStableId(itemsLocation),
                itemBindingId,
                translateSequence(AttributeLocation(location, ScriptConventions.stepsAttributePath)))
        }


        private fun doWhileStep(location: ObjectLocation): DoWhileStep {
            val conditionCode = stringAttribute(location, conditionAttributePath)
            val conditionScope = doWhileConditionScope(location)
            val body = translateSequence(AttributeLocation(location, ScriptConventions.stepsAttributePath))
            return DoWhileStep(objectStableMapper.objectStableId(location), body) { runContext ->
                StepExpressionSupport.evaluate(
                    location, "Boolean", conditionCode, conditionScope,
                    resolver(runContext), cachedKotlinCompiler) as? Boolean
                    ?: error("Do-while condition is not a boolean: $location")
            }
        }


        // A nested sub-Script invocation: compile the linked Script and host it as a confined child node,
        // resolving each declared argument from this run's in-scope values at call time. Only Script targets
        // are ported; a non-Script linked logic (e.g. a Flow) throws until those flavours are ported.
        private fun runStep(location: ObjectLocation): RunStep {
            val instructionsLocation = RunStepInstructions.instructionsLocation(graphNotation, location)
                ?: error("RunStep has no instructions reference: $location")

            val instructionsDocument = graphNotation.documents[instructionsLocation.documentPath]
                ?: error("RunStep instructions document not found: $instructionsLocation")
            if (! ScriptConventions.isScript(instructionsDocument)) {
                throw NotImplementedError(
                    "RunStep target is not a Script (only Script children ported): $instructionsLocation")
            }

            val childLogic = compile(
                instructionsLocation, graphNotation, graphDefinition,
                graphEnvironment, objectStableMapper, cachedKotlinCompiler)

            val bindings = argumentBindings(location)

            return RunStep(
                objectStableMapper.objectStableId(location),
                objectStableMapper.objectStableId(instructionsLocation),
                childLogic
            ) { runContext ->
                TupleValue(bindings.map { (name, argumentLocation) ->
                    TupleComponentValue(
                        name,
                        runContext.referencedValue(objectStableMapper.objectStableId(argumentLocation)))
                })
            }
        }


        private fun argumentBindings(location: ObjectLocation): List<Pair<TupleComponentName, ObjectLocation>> {
            val argumentsMap = (graphNotation.firstAttribute(location, argumentsAttributePath)
                as? MapAttributeNotation)
                ?.map
                ?: return listOf()

            return argumentsMap.mapNotNull { (segment, referenceNotation) ->
                val reference = (referenceNotation as? ScalarAttributeNotation)?.value
                    ?: return@mapNotNull null
                val argumentLocation = graphNotation.coalesce.locateOptional(
                    ObjectReference.parse(reference),
                    ObjectReferenceHost.ofLocation(location))
                    ?: return@mapNotNull null
                TupleComponentName(segment.asKey()) to argumentLocation
            }
        }


        // A literal / range step has no expression — it produces a fixed value, recorded like any other step.
        private fun literalStep(location: ObjectLocation, value: Any?): FormulaStep {
            return FormulaStep(objectStableMapper.objectStableId(location)) { value }
        }


        //------------------------------------------------------------------------------------------- attribute reads
        private fun stringAttribute(location: ObjectLocation, attributePath: AttributePath): String {
            return (graphNotation.firstAttribute(location, attributePath) as? ScalarAttributeNotation)
                ?.value
                ?: error("Missing '$attributePath' attribute: $location")
        }


        private fun longAttribute(location: ObjectLocation, attributePath: AttributePath): Long {
            return stringAttribute(location, attributePath).toLong()
        }


        private fun booleanAttribute(location: ObjectLocation, attributePath: AttributePath): Boolean {
            return stringAttribute(location, attributePath).toBooleanStrict()
        }


        private fun doubleAttribute(location: ObjectLocation, attributePath: AttributePath): Double {
            return stringAttribute(location, attributePath).toDouble()
        }


        private fun rangeValue(location: ObjectLocation): List<Int> {
            val from = stringAttribute(location, fromAttributePath).toInt()
            val to = stringAttribute(location, toAttributePath).toInt()
            return (from .. to).toList()
        }


        private fun parameterDefault(location: ObjectLocation): Any? {
            val defaultText = (graphNotation.firstAttribute(location, defaultAttributePath)
                as? ScalarAttributeNotation)
                ?.value
                ?: return null
            val type = graphNotation.firstAttribute(location, typeAttributePath)
                ?.let { TypeMetadataDefiner.parse(it) }
                ?: return null
            return ParameterDefaultDefiner.coerce(defaultText, type)
        }


        private fun nonUnitTypesOf(location: ObjectLocation): Map<ObjectPath, TypeMetadata> {
            return StepExpressionSupport.resolveNonUnit(
                StepExpressionSupport.inScopeTypes(location, scriptTree, scriptValidation))
                ?: error("Unresolved in-scope types for: $location")
        }


        // The do-while condition's scope is the loop's body steps (not predecessors) plus the in-scope
        // bindings — mirroring DoWhileStep.conditionScopeTypes — with Unit-typed values dropped.
        private fun doWhileConditionScope(location: ObjectLocation): Map<ObjectPath, TypeMetadata> {
            val bodyPaths = ScriptConventions.orderedDirectChildLocations(
                graphNotation, AttributeLocation(location, ScriptConventions.stepsAttributePath))
                .map { it.objectPath }
            val bindingPaths = scriptTree.inScopeBindingPaths(location.objectPath)

            val scope = LinkedHashMap<ObjectPath, TypeMetadata>()
            for (path in bodyPaths + bindingPaths) {
                val typeMetadata = scriptValidation.stepValidations[path]?.typeMetadata
                    ?: error("Unresolved do-while condition scope type for $path: $location")
                if (typeMetadata.className != ClassNames.kotlinUnit) {
                    scope[path] = typeMetadata
                }
            }
            return scope
        }


        private fun referenceLocation(
            location: ObjectLocation,
            attributePath: AttributePath
        ): ObjectLocation? {
            val reference = (graphNotation.firstAttribute(location, attributePath) as? ScalarAttributeNotation)
                ?.value
                ?: return null
            return graphNotation.coalesce.locateOptional(
                ObjectReference.parse(reference),
                ObjectReferenceHost.ofLocation(location))
        }


        private fun resolver(runContext: ScriptRunContext): (ObjectLocation) -> Any? {
            return { objectLocation ->
                runContext.referencedValue(objectStableMapper.objectStableId(objectLocation))
            }
        }
    }
}
