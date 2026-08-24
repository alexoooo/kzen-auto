package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.job.JobSignatureCapability
import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.common.objects.document.logic.ResultSignatureDefiner
import tech.kzen.auto.common.objects.document.flow.FlowConventions
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionContext
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumn
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumnEval
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleComponentDefinition
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/**
 * Invokes another Logic ([instructions] — a Script / Flow / Job) as a child, once per incoming element: the
 * element is passed as the child's first parameter and the child's main result is emitted downstream. The Job
 * analogue of a Script's Run step ([tech.kzen.auto.server.objects.script.step.control.RunStep]) and a Flow's
 * Run-Logic vertex ([tech.kzen.auto.server.objects.flow.vertex.RunLogicVertex]) — the seam that lets a Job
 * compose reusable sub-Logics into its dataflow rather than only built-in stages.
 * When [arguments] is non-empty, the incoming boundary value still binds the child's first input and every
 * additional Job input must have one Kotlin expression. Expressions share Formula's received payload, received
 * flat columns, and outer-Job parameter scope; their raw scalar values keep their runtime types.
 *
 * A LOGIC-BOUNDARY worker: a [JobMessage] never crosses into the child, so each incoming message unwraps via
 * [JobMessage.boundaryValue] (payload when present, else the flat part as an ordered Map) and the child's main
 * result wraps as a fresh payload message. A [TransformWorker], so the framework owns the drain loop, per-batch
 * [JobControl.checkpoint], throttled progress, and end-of-stream close propagation; this Worker only maps each
 * element through its child Logic via [JobControl.host].
 *
 * Stepping and pause-on-error are engine-driven and need no code here: [JobControl.host] hosts the child under
 * this Worker's own engine node, so Step Into descends into the child and a child that halts (a Pause step, or a
 * step parked under pause-on-error for fix + resume) leaves the host call suspended and pauses the whole Job at
 * a quiescent wavefront — the old re-entrant driver's `continueOrStart` / `requestHalt` loop collapses into the
 * single [JobControl.host] call.
 */
@Reflect
class RunWorker(
    input: ChannelInput<Any?>,
    output: ChannelOutput<Any?>,

    private val instructions: ObjectLocation,
    private val arguments: Map<String, String>,
    private val selfLocation: ObjectLocation,
    @Service private val calculatedColumnEval: CalculatedColumnEval
):
    TransformWorker(input, output, selfLocation)
{
    private val classLoader = ClassLoaderUtils.dynamicParentClassLoader()
    private val emptyRecord = FlatFileRecord()
    private var childSignature: LogicSignature? = null
    private var childIsJob = false
    private var compiledForHeader: HeaderListing? = null
    private var compiledArguments: List<Pair<String, CalculatedColumn<Any?>>> = listOf()
    private var ran = 0L


    override suspend fun onElement(element: JobMessage, emit: Emitter, control: JobControl) {
        val result =
            if (arguments.isEmpty()) {
                control.host(instructions = instructions, input = element.boundaryValue())
            }
            else {
                val signature = childSignature
                    ?: error("Unable to bind Run arguments: child signature was not resolved for $instructions")
                requireCompleteJobArguments(signature)
                val receivedFlat = element.flat
                val header = receivedFlat?.header ?: HeaderListing.empty
                if (header != compiledForHeader) {
                    compileArguments(header, control)
                }

                val first = signature.inputs.components.firstOrNull()?.name
                    ?: error("Run child declares no input parameter: $instructions")
                val components = mutableListOf(
                    TupleComponentValue(first, element.boundaryValue()))
                for ((name, compiled) in compiledArguments) {
                    val value = compiled.evaluateRaw(
                        element.payload, receivedFlat?.record ?: emptyRecord, header)
                    require(value is String || value is Char || value is Boolean || value is Number) {
                        "Run argument '$name' must evaluate to String, Char, Boolean, or Number; found " +
                            (value?.let { it::class.qualifiedName } ?: "null")
                    }
                    components.add(TupleComponentValue(TupleComponentName(name), value))
                }
                control.host(instructions = instructions, arguments = TupleValue(components))
            }
        ran += 1
        emit.send(JobMessage.ofPayload(result.mainComponentValue()))
    }


    private fun requireCompleteJobArguments(signature: LogicSignature) {
        if (!childIsJob) {
            return
        }
        signature.inputs.components.drop(1).firstOrNull { it.name.value !in arguments }?.let {
            throw IllegalArgumentException("Missing Run argument '${it.name.value}' for $instructions")
        }
    }


    private suspend fun compileArguments(header: HeaderListing, control: JobControl) {
        val parameters = control.parameters()
        val receiverType = control.payloadType() ?: TypeMetadata.anyNullable
        val compiled = control.runBlockingIo {
            arguments.map { (name, expression) ->
                name to calculatedColumnEval.create(
                    "argument_$name", expression, header, receiverType,
                    classLoader, parameters)
            }
        }
        val parameterValues = parameters.components.map { control.parameter(it.name.value) }
        compiled.forEach { it.second.setParameters(parameterValues) }
        compiledArguments = compiled
        compiledForHeader = header
    }


    override fun loadDefinitionContext(context: WorkerDefinitionContext) {
        childSignature = childSignature(context.graphStructure())
    }


    override fun progress(snapshot: Any?): Map<String, Any?> =
        mapOf("ran" to ran)


    //-----------------------------------------------------------------------------------------------------------------
    // The nested-Logic host emits its child's main result as a fresh payload, so the output lane's type is
    // the callee's declared main result type (the RunStep precedent): a Script callee declares it in its
    // `results` signature, a Job callee derives it via JobSignatureCapability; any other flavour (or a void
    // callee) approximates to nullable Any. No flat part on the output lane (the child result is a payload).
    override fun payloadFlow(input: WorkerLane, context: WorkerLaneContext): WorkerLaneAttempt {
        val graphNotation = context.graphStructure.graphNotation
        val instructionsDocument = graphNotation.documents[instructions.documentPath]

        val signature = childSignature(context.graphStructure)
        childSignature = signature
        val argumentError = validateArguments(input, context, signature)

        val childMainType: TypeMetadata =
            when {
                instructionsDocument == null ->
                    TypeMetadata.anyNullable

                ScriptConventions.isScript(instructionsDocument) ->
                    ResultSignatureDefiner
                        .parse(graphNotation.firstAttribute(
                            instructions, ScriptConventions.resultsAttributePath))
                        .find(TupleComponentName.main)
                        ?.metadata
                        ?: TypeMetadata.anyNullable

                JobConventions.isJob(instructionsDocument) ->
                    signature!!
                        .outputs
                        .find(TupleComponentName.main)
                        ?.metadata
                        ?: TypeMetadata.anyNullable

                else ->
                    TypeMetadata.anyNullable
            }

        return WorkerLaneAttempt(WorkerLane(childMainType, HeaderListing.empty), argumentError)
    }


    private fun childSignature(graphStructure: tech.kzen.lib.common.model.structure.GraphStructure): LogicSignature? {
        val graphNotation = graphStructure.graphNotation
        val document = graphNotation.documents[instructions.documentPath]
            ?: return null
        childIsJob = JobConventions.isJob(document)
        return when {
            childIsJob -> JobSignatureCapability.signature(graphStructure, instructions)
            ScriptConventions.isScript(document) -> {
                val inputs = ScriptConventions.orderedDirectChildLocations(
                    graphNotation,
                    AttributeLocation(instructions, ScriptConventions.parametersAttributePath))
                    .map {
                        TupleComponentDefinition(
                            TupleComponentName(it.objectPath.name.value), LogicType.any)
                    }
                val outputs = ResultSignatureDefiner.parse(
                    graphNotation.firstAttribute(instructions, ScriptConventions.resultsAttributePath))
                LogicSignature(TupleDefinition(inputs), outputs)
            }
            FlowConventions.isFlow(document) -> {
                val inputs = FlowConventions.inputParameterNames(graphNotation, instructions).map {
                    TupleComponentDefinition(TupleComponentName(it), LogicType.any)
                }
                LogicSignature(TupleDefinition(inputs), TupleDefinition.empty)
            }
            else -> null
        }
    }


    private fun validateArguments(
        input: WorkerLane,
        context: WorkerLaneContext,
        signature: LogicSignature?
    ): String? {
        if (arguments.isEmpty()) {
            return null
        }
        val inputs = signature?.inputs?.components
            ?: return "Named Run arguments require a Job callee with a declared signature"
        val first = inputs.firstOrNull()?.name?.value
            ?: return "Run child declares no input parameter: $instructions"
        if (first in arguments) {
            return "Run argument '$first' duplicates the positionally-bound first input"
        }
        val inputNames = inputs.map { it.name.value }
        arguments.keys.firstOrNull { it !in inputNames }?.let {
            return "Unknown Run argument '$it' for $instructions"
        }
        if (childIsJob) {
            inputNames.drop(1).firstOrNull { it !in arguments }?.let {
                return "Missing Run argument '$it' for $instructions"
            }
        }

        val columns = input.flatColumns
        val receiverType = input.payloadType ?: TypeMetadata.anyNullable
        for ((name, expression) in arguments) {
            val error =
                if (columns == null && input.payloadType == null) {
                    calculatedColumnEval.validateSyntax(expression)
                }
                else {
                    calculatedColumnEval.validate(
                        "argument_$name", expression, columns ?: HeaderListing.empty, receiverType,
                        context.classLoader, context.parameters)
                }
            if (error != null) {
                return "$name: $error"
            }
        }
        return null
    }
}
