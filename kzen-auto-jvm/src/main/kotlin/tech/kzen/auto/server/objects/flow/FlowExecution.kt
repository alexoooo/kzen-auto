package tech.kzen.auto.server.objects.flow

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.paradigm.flow.api.FlowVertex
import tech.kzen.auto.common.paradigm.flow.api.StreamFlowVertex
import tech.kzen.auto.common.paradigm.flow.model.channel.MutableFlowOutput
import tech.kzen.auto.common.paradigm.flow.model.channel.MutableInput
import tech.kzen.auto.common.paradigm.flow.model.exec.ActiveVertexModel
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualFlowModel
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowDag
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix
import tech.kzen.auto.common.paradigm.flow.service.format.FlowMessageInspector
import tech.kzen.auto.common.paradigm.flow.util.FlowUtils
import tech.kzen.auto.server.objects.flow.vertex.FlowInputVertex
import tech.kzen.auto.server.objects.flow.vertex.FlowOutputVertex
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.logic.LogicControl
import tech.kzen.lib.common.exec.logic.LogicExecution
import tech.kzen.lib.common.exec.logic.LogicResourceScope
import tech.kzen.lib.common.exec.logic.model.LogicCommand
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultCancelled
import tech.kzen.lib.common.exec.logic.model.LogicResultFailed
import tech.kzen.lib.common.exec.logic.model.LogicResultPaused
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.trace.LogicTraceHandle
import tech.kzen.lib.common.exec.logic.trace.model.LogicTracePath
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.instance.ObjectInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.common.util.ExceptionUtils
import tech.kzen.lib.platform.collect.toPersistentMap


/**
 * Runs a [FlowDocument]'s dataflow DAG under the kzen-lib Logic/Execution model: one vertex execution
 * per "step", driven by [tech.kzen.auto.server.service.impl.ServerLogicController] via repeated
 * [continueOrStart] passes. Mirrors [tech.kzen.auto.server.objects.script.ScriptExecution] /
 * [tech.kzen.auto.server.objects.script.step.control.MultiStep]: the same execution instance is reused
 * across pause/step/resume (the controller only [close]s on a terminal result), so partial DAG progress
 * lives in instance fields ([activeVertices], [outputAccumulator], [arguments]) rather than a
 * StatefulLogicElement.
 *
 * Migrated from the retired [tech.kzen.auto.common.paradigm.flow.service.active.ActiveDataflowRepository]:
 * the per-vertex runtime model, input population, and loop/iteration clearing are preserved, but re-keyed
 * by [ObjectStableId] (rename-safe) and each vertex output is recorded to [LogicTraceHandle] as the same
 * [VisualVertexModel] JSON the old `/dataflow/model` endpoint returned, so the client rebuilds its visual
 * model from traces (like the Script progress view).
 */
class FlowExecution(
    private val documentPath: DocumentPath,
    private val logicTraceHandle: LogicTraceHandle,
    private val objectStableMapper: ObjectStableMapper,
    private val graphCreator: GraphCreator,
    private val environment: GraphEnvironment,
    private val flowMessageInspector: FlowMessageInspector
):
    LogicExecution
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(FlowExecution::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Per-vertex runtime, keyed by stable id so it survives renames across pause/resume.
    private val activeVertices = mutableMapOf<ObjectStableId, ActiveVertexModel>()

    // Output vertices' captured values, harvested into the result TupleValue on terminal success.
    private val outputAccumulator = mutableMapOf<TupleComponentName, Any?>()

    private var arguments = TupleValue.empty


    //-----------------------------------------------------------------------------------------------------------------
    override fun beforeStart(arguments: TupleValue): Boolean {
        // Store the run arguments; do NOT reset activeVertices — a resume re-enters partial progress.
        this.arguments = arguments
        return true
    }


    override fun continueOrStart(
        logicControl: LogicControl,
        resourceScope: LogicResourceScope,
        graphDefinition: GraphDefinition
    ): LogicResult {
        if (logicControl.pollCommand() == LogicCommand.Cancel) {
            return LogicResultCancelled
        }

        val graphStructure = graphDefinition.graphStructure
        val matrix = FlowMatrix.ofDocument(documentPath, graphStructure)

        val dag =
            try {
                FlowDag.of(matrix)
            }
            catch (e: Exception) {
                return LogicResultFailed("Flow structure error: ${ExceptionUtils.message(e)}")
            }

        // Drop runtime entries for vertices that no longer exist (e.g. deleted between passes).
        val liveStableIds = matrix.verticesByLocation.keys
            .map { stableId(it) }
            .toSet()
        activeVertices.keys.retainAll(liveStableIds)

        // The first vertex after a resume/step must run even though the controller pre-arms a Pause
        // command (see ServerLogicController.step / continueOrStart) — mirrors MultiStep.
        var executeNextIfPaused = true

        while (true) {
            var next = FlowUtils.next(matrix, dag, snapshotVisual(matrix))

            if (next == null) {
                if (activeVertices.values.none { it.hasNext() }) {
                    // Nothing left to run and no stream/batch remainder: the run is complete. Final
                    // vertex messages are left intact (no clearing) so the result values stay visible.
                    return LogicResultSuccess(outputTuple())
                }

                // A source still has buffered items (stream/batch): clear the in-flight layer + reset
                // downstream so the next iteration re-flows, then re-select.
                clearIterationForLoop(dag)
                next = FlowUtils.next(matrix, dag, snapshotVisual(matrix))
                    ?: return LogicResultSuccess(outputTuple())
            }

            val command = logicControl.pollCommand()
            if (command == LogicCommand.Cancel) {
                return LogicResultCancelled
            }
            else if (!executeNextIfPaused && command == LogicCommand.Pause) {
                return LogicResultPaused
            }
            executeNextIfPaused = false

            val nextStableId = stableId(next)
            val instance = createInstance(next, graphDefinition)
            seedIfAbsent(nextStableId, instance)

            traceVertex(next, nextStableId, instance, running = true)

            try {
                runOneVertex(next, nextStableId, instance, matrix)
            }
            catch (t: Throwable) {
                // Leave epoch un-advanced so the failed vertex stays "next to run" — on resume it is
                // re-attempted (a fix-then-resume), matching MultiStep's pause-on-error behaviour.
                val message = ExceptionUtils.message(t)
                activeVertices[nextStableId]?.error = message
                logger.warn("Vertex error - {}", next, t)
                traceVertex(next, nextStableId, instance, running = false)

                return if (logicControl.pauseOnError()) {
                    LogicResultPaused
                }
                else {
                    LogicResultFailed(message)
                }
            }

            traceVertex(next, nextStableId, instance, running = false)

            val reference = instance.reference
            if (reference is FlowOutputVertex) {
                outputAccumulator[reference.tupleComponentName] = activeVertices[nextStableId]?.message
            }
        }
    }


    override fun close(error: Boolean) {
        logger.info("{} - close - {}", documentPath, error)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun runOneVertex(
        vertexLocation: ObjectLocation,
        stableId: ObjectStableId,
        instance: ObjectInstance,
        matrix: FlowMatrix
    ) {
        val activeVertexModel = activeVertices[stableId]!!

        // Drain one buffered batch item, if any — this counts as one step (one vertex execution).
        if (activeVertexModel.remainingBatch.isNotEmpty()) {
            activeVertexModel.message = activeVertexModel.remainingBatch.removeAt(0)
            activeVertexModel.epoch++
            return
        }

        val reference = instance.reference

        // Input vertices have no upstream; their message is the named run argument.
        if (reference is FlowInputVertex) {
            activeVertexModel.message = arguments.find(reference.tupleComponentName)
            activeVertexModel.epoch++
            return
        }

        @Suppress("UNCHECKED_CAST")
        val flowVertex = reference as FlowVertex<Any?>

        populateInputs(instance, vertexLocation, matrix)

        val nextState =
            when {
                activeVertexModel.streamHasNext ->
                    (flowVertex as StreamFlowVertex<Any?>).next(activeVertexModel.state)

                activeVertexModel.state == null -> {
                    flowVertex.process(Unit)
                    null
                }

                else ->
                    flowVertex.process(activeVertexModel.state)
            }

        val output = instance
            .constructorAttributes[FlowUtils.mainOutputAttributeName] as? MutableFlowOutput<*>
        if (output != null) {
            if (output.bufferHasMultiple()) {
                output.consumeAndClear {
                    if (activeVertexModel.message == null) {
                        activeVertexModel.message = it
                    }
                    else {
                        activeVertexModel.remainingBatch.add(it!!)
                    }
                }
            }
            else {
                activeVertexModel.message = output.getAndClear()
            }
            activeVertexModel.streamHasNext = output.streamHasNext()
        }

        activeVertexModel.state = nextState
        activeVertexModel.epoch++
    }


    private fun populateInputs(
        instance: ObjectInstance,
        vertexLocation: ObjectLocation,
        matrix: FlowMatrix
    ) {
        val vertexDescriptor = matrix.verticesByLocation[vertexLocation]
            ?: throw IllegalStateException("Vertex not found in matrix: $vertexLocation")

        val inputAttributes = vertexDescriptor.inputNames
        if (inputAttributes.isEmpty()) {
            return
        }

        var populatedInputCount = 0
        for (inputAttribute in inputAttributes) {
            val sourceVertex = matrix.traceVertexBackFrom(vertexDescriptor, inputAttribute)
                ?: continue

            @Suppress("UNCHECKED_CAST")
            val input = instance.constructorAttributes[inputAttribute] as? MutableInput<Any>
                ?: throw IllegalArgumentException("Unknown input $inputAttribute on $vertexLocation")

            val message = activeVertices[stableId(sourceVertex.objectLocation)]?.message
            if (message != null) {
                input.set(message)
            }
            else {
                input.clear()
            }

            populatedInputCount++
        }

        check(populatedInputCount > 0) {
            "Vertex must receive at least one input: $vertexLocation"
        }
    }


    /**
     * Start the next loop iteration when a source still has buffered stream/batch items: clear the
     * messages on the last layer that still hasNext (so it re-emits) and reset epochs on the layers
     * below it (so they re-run). Migrated verbatim from ActiveDataflowRepository.clearIteration's
     * "lastRowWithNextMessage != -1" branch — the "-1" branch (full reset at end of run) is replaced by
     * the terminal LogicResultSuccess in [continueOrStart].
     */
    private fun clearIterationForLoop(dag: FlowDag) {
        val lastRowWithNext = dag.layers.indexOfLast { layer ->
            layer.any { activeVertices[stableId(it)]?.hasNext() ?: false }
        }
        if (lastRowWithNext == -1) {
            return
        }

        for (vertexLocation in dag.layers[lastRowWithNext]) {
            val model = activeVertices[stableId(vertexLocation)]
                ?: continue
            model.message = null
        }

        for (followingLayer in dag.layers.subList(lastRowWithNext + 1, dag.layers.size)) {
            for (vertexLocation in followingLayer) {
                val downstreamStableId = stableId(vertexLocation)
                val model = activeVertices[downstreamStableId]
                    ?: continue
                if (model.epoch > 0) {
                    model.epoch = 0
                    model.message = null

                    // New clock cycle: drop the downstream vertex's live trace so the client repaints
                    // it neutral instead of lingering on the previous cycle's message (mirrors
                    // DoWhileStep.resetSteps' logicTraceHandle.clearAll per loop iteration).
                    logicTraceHandle.clearAll(LogicTracePath.ofObjectStableId(downstreamStableId))
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun seedIfAbsent(stableId: ObjectStableId, instance: ObjectInstance) {
        if (stableId in activeVertices) {
            return
        }

        val initialState = (instance.reference as FlowVertex<*>).initialState()
        val initialStateOrNull =
            if (initialState == Unit) {
                null
            }
            else {
                initialState
            }

        activeVertices[stableId] = ActiveVertexModel(
            initialStateOrNull,
            null,
            mutableListOf(),
            false,
            0,
            null)
    }


    private fun createInstance(
        vertexLocation: ObjectLocation,
        graphDefinition: GraphDefinition
    ): ObjectInstance {
        // A fresh instance per execution gives clean injected channels (MutableInput / output buffer),
        // matching the old engine's per-execution instance creation.
        val graphInstance = graphCreator.createGraph(
            graphDefinition.filterTransitive(documentPath), environment)
        return graphInstance[vertexLocation]
            ?: throw IllegalStateException("Vertex not found: $vertexLocation")
    }


    /**
     * Routing snapshot for [FlowUtils.next]. It reads only message-presence, hasNext, epoch and
     * running — never the inspected message/state — so this avoids per-iteration message inspection.
     * Absent vertices route as [VisualVertexModel.empty] (pending, epoch 0), the correct pre-run state.
     */
    private fun snapshotVisual(matrix: FlowMatrix): VisualFlowModel {
        val builder = mutableMapOf<ObjectLocation, VisualVertexModel>()
        for (vertexLocation in matrix.verticesByLocation.keys) {
            val model = activeVertices[stableId(vertexLocation)]
            builder[vertexLocation] =
                if (model == null) {
                    VisualVertexModel.empty
                }
                else {
                    VisualVertexModel(
                        false,
                        null,
                        if (model.message != null) NullExecutionValue else null,
                        model.hasNext(),
                        model.epoch.toInt(),
                        model.error)
                }
        }
        return VisualFlowModel(builder.toPersistentMap())
    }


    private fun traceVertex(
        vertexLocation: ObjectLocation,
        stableId: ObjectStableId,
        instance: ObjectInstance,
        running: Boolean
    ) {
        val model = activeVertices[stableId]
            ?: return

        val stateValue = model.state?.let {
            @Suppress("UNCHECKED_CAST")
            (instance.reference as FlowVertex<Any>).inspectState(it)
        }

        val messageValue = model.message?.let {
            flowMessageInspector.inspectMessage(it)
        }

        val visualVertexModel = VisualVertexModel(
            running,
            stateValue,
            messageValue,
            model.hasNext(),
            model.epoch.toInt(),
            model.error)

        logicTraceHandle.set(
            LogicTracePath.ofObjectStableId(stableId),
            ExecutionValue.of(VisualVertexModel.toJsonCollection(visualVertexModel)))
    }


    private fun outputTuple(): TupleValue {
        return TupleValue(
            outputAccumulator.map { TupleComponentValue(it.key, it.value) })
    }


    private fun stableId(objectLocation: ObjectLocation): ObjectStableId {
        return objectStableMapper.objectStableId(objectLocation)
    }
}
