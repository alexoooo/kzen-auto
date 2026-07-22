package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.job.JobSignatureCapability
import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.logic.ResultSignatureDefiner
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect


/**
 * Invokes another Logic ([instructions] — a Script / Flow / Job) as a child, once per incoming element: the
 * element is passed as the child's first parameter and the child's main result is emitted downstream. The Job
 * analogue of a Script's Run step ([tech.kzen.auto.server.objects.script.step.control.RunStep]) and a Flow's
 * Run-Logic vertex ([tech.kzen.auto.server.objects.flow.vertex.RunLogicVertex]) — the seam that lets a Job
 * compose reusable sub-Logics into its dataflow rather than only built-in stages.
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
    selfLocation: ObjectLocation
):
    TransformWorker(input, output, selfLocation)
{
    private var ran = 0L


    override suspend fun onElement(element: JobMessage, emit: Emitter, control: JobControl) {
        val result = control.host(instructions, element.boundaryValue())
        ran += 1
        emit.send(JobMessage.ofPayload(result.mainComponentValue()))
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
                    JobSignatureCapability
                        .signature(context.graphStructure, instructions)
                        .outputs
                        .find(TupleComponentName.main)
                        ?.metadata
                        ?: TypeMetadata.anyNullable

                else ->
                    TypeMetadata.anyNullable
            }

        return WorkerLaneAttempt(WorkerLane(childMainType, HeaderListing.empty), null)
    }
}
