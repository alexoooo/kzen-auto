package tech.kzen.auto.server.exec.flow

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
import tech.kzen.auto.server.objects.flow.vertex.RunLogicVertex
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.LogicFailure
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
 * One run of a [FlowLogic]'s dataflow DAG on the new engine: the coroutine-shaped successor to
 * [tech.kzen.auto.server.objects.flow.FlowExecution]'s re-entrant `continueOrStart`. Position lives on this
 * coroutine's stack, so a vertex is a step boundary ([Execution.checkpoint] before running it) and the engine
 * drives pause / step / cancel centrally — there is no pollCommand / budget / depth arithmetic, and a
 * [RunLogicVertex] is just [Execution.host] (no [tech.kzen.auto.server.objects.flow.FlowExecution] `pausedChildren`
 * cache: the suspension is held on the host call's frame).
 *
 * The per-vertex mechanics (input population, batch/stream draining, loop-iteration clearing, and the
 * [VisualVertexModel] tracing the client rebuilds its visual model from) are ported from [FlowExecution]; the
 * trace is emitted with [Execution.emit] at the vertex's stable-id address, which the controller's trace bridge
 * routes back to the same per-vertex trace path the old store used.
 *
 * Pause-on-error (logic-spec §4) is wired via [Execution.recoverable] around each vertex: a vertex failure is
 * rendered (error + trace) and, when pause-on-error is enabled, parks the vertex Suspended(Error) for fix +
 * resume (re-running it on resume) rather than failing the run; with the toggle off the failure propagates.
 *
 * Live-edit migration (logic-spec §5): the per-vertex progress ([activeVertices]) and harvested output
 * ([outputAccumulator]) are carried across a pause -> edit -> resume by the Flow root node's
 * [Execution.onCapture] / [Execution.restored] as a [FlowMigrationState] (keyed by stable id), so the rebuilt
 * run continues from the live frontier instead of restarting — the clean-room successor to the way the retired
 * [tech.kzen.auto.server.objects.flow.FlowExecution] kept these same stable-id-keyed fields alive across
 * `continueOrStart` re-entries.
 */
class FlowRun(
    private val execution: Execution,
    private val documentPath: DocumentPath,
    private val graphDefinition: GraphDefinition,
    private val childLogics: Map<ObjectStableId, FlowChildLogic>,
    private val objectStableMapper: ObjectStableMapper,
    private val flowMessageInspector: FlowMessageInspector,
    private val graphEnvironment: GraphEnvironment
) {
    //-----------------------------------------------------------------------------------------------------------------
    // Per-vertex runtime, keyed by stable id so it survives renames; output vertices' harvested values.
    private val activeVertices = mutableMapOf<ObjectStableId, ActiveVertexModel>()
    private val outputAccumulator = mutableMapOf<TupleComponentName, Any?>()


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun run(): TupleValue {
        // Live-edit migration (logic-spec §5): adopt the predecessor run's DAG progress (read once at start) so
        // the walker continues from the carried frontier, and register the capture so a later edit carries this
        // run's progress forward. Null on a fresh run -> nothing to adopt, the DAG runs from the start.
        (execution.restored as? FlowMigrationState)?.let { carried ->
            activeVertices.putAll(carried.activeVertices)
            outputAccumulator.putAll(carried.outputAccumulator)
        }
        execution.onCapture {
            FlowMigrationState(activeVertices.toMap(), outputAccumulator.toMap())
        }

        val matrix = FlowMatrix.ofDocument(documentPath, graphDefinition.graphStructure)
        val dag =
            try {
                FlowDag.of(matrix)
            }
            catch (e: Exception) {
                throw LogicFailure("Flow structure error: ${ExceptionUtils.message(e)}")
            }

        while (true) {
            var next = FlowUtils.next(matrix, dag, snapshotVisual(matrix))

            if (next == null) {
                if (activeVertices.values.none { it.hasNext() }) {
                    // Run complete (nothing routable, no stream/batch remainder): clear lingering in-flight
                    // messages — keeping displayed state — and return the harvested output.
                    clearMessagesAtEnd(matrix)
                    return outputTuple()
                }

                // A source still has buffered items (stream/batch): start the next iteration, then re-select.
                clearIterationForLoop(dag)
                next = FlowUtils.next(matrix, dag, snapshotVisual(matrix))
                if (next == null) {
                    clearMessagesAtEnd(matrix)
                    return outputTuple()
                }
            }

            // The vertex is the step boundary: settle BEFORE running it, so a paused / stepping run pauses
            // here (the engine drives stepping by depth; the client highlights FlowUtils.next as "next").
            execution.checkpoint()

            val nextStableId = stableId(next)
            val instance = createInstance(next)
            seedIfAbsent(nextStableId, instance)

            traceVertex(nextStableId, instance, running = true)

            val reference = instance.reference

            // RunLogicVertex invokes another Logic as a confined child node (steppable via the engine tree):
            // Step Into descends into it; Step Over / Step Out / Run run it to completion.
            if (reference is RunLogicVertex) {
                runChildVertex(next, nextStableId, instance, matrix)
                traceVertex(nextStableId, instance, running = false)
                continue
            }

            // Pause-on-error (logic-spec §4): the engine renders the vertex failure (error + trace) then, if
            // pause-on-error is on, parks the vertex Suspended(Error) for fix + resume and re-runs it on resume;
            // if off, the failure propagates and the run fails.
            execution.recoverable({ t ->
                activeVertices[nextStableId]?.error = ExceptionUtils.message(t)
                traceVertex(nextStableId, instance, running = false)
            }) {
                runOneVertex(next, nextStableId, instance, matrix)
            }

            traceVertex(nextStableId, instance, running = false)

            if (reference is FlowOutputVertex) {
                outputAccumulator[reference.tupleComponentName] = activeVertices[nextStableId]?.message
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Run a [RunLogicVertex]'s pre-compiled callee as a confined child node ([Execution.host]) — the Flow
     * analogue of [RunStep][tech.kzen.auto.server.objects.script.step.control.RunStep]. The single upstream input is
     * passed as the callee's first declared parameter; on success the callee's main result becomes the
     * vertex's message (which downstream vertices wire to).
     */
    private suspend fun runChildVertex(
        vertexLocation: ObjectLocation,
        stableId: ObjectStableId,
        instance: ObjectInstance,
        matrix: FlowMatrix
    ) {
        val activeVertexModel = activeVertices[stableId]!!
        val childLogic = childLogics[stableId]
            ?: throw LogicFailure("RunLogicVertex child not compiled: $vertexLocation")

        val argumentMessage = singleInputMessage(vertexLocation, matrix)
        val inputs =
            if (childLogic.firstParameterName != null) {
                TupleValue(listOf(TupleComponentValue(childLogic.firstParameterName, argumentMessage)))
            }
            else {
                TupleValue.empty
            }

        // Pause-on-error for a hosted child vertex: same recoverable contract as a regular vertex — render the
        // failure, then park Error (fix + resume re-hosts) or propagate. A child cancel always propagates.
        val result = execution.recoverable({ t ->
            activeVertexModel.error = ExceptionUtils.message(t)
            traceVertex(stableId, instance, running = false)
        }) {
            execution.host(childLogic.childStableId, childLogic.logic, inputs)
        }

        activeVertexModel.message = result.mainComponentValue()
        activeVertexModel.epoch++
    }


    // The single upstream input message for a vertex (its sole wired input). Null when nothing is wired / no
    // message has arrived yet.
    private fun singleInputMessage(vertexLocation: ObjectLocation, matrix: FlowMatrix): Any? {
        val vertexDescriptor = matrix.verticesByLocation[vertexLocation]
            ?: return null
        val inputAttribute = vertexDescriptor.inputNames.firstOrNull()
            ?: return null
        val sourceVertex = matrix.traceVertexBackFrom(vertexDescriptor, inputAttribute)
            ?: return null
        return activeVertices[stableId(sourceVertex.objectLocation)]?.message
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
            activeVertexModel.message = execution.inputs.find(reference.tupleComponentName)
            activeVertexModel.epoch++
            return
        }

        // Output (sink) vertices have no output channel: capture the single upstream input as this vertex's
        // message, which the run loop then harvests into the result tuple.
        if (reference is FlowOutputVertex) {
            activeVertexModel.message = singleInputMessage(vertexLocation, matrix)
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


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Start the next loop iteration when a source still has buffered stream/batch items: clear the messages on
     * the last layer that still hasNext (so it re-emits) and reset epochs on the layers below it (so they
     * re-run). Ported from [tech.kzen.auto.server.objects.flow.FlowExecution.clearIterationForLoop].
     */
    private fun clearIterationForLoop(dag: FlowDag) {
        val lastRowWithNext = dag.layers.indexOfLast { layer ->
            layer.any { activeVertices[stableId(it)]?.hasNext() ?: false }
        }
        if (lastRowWithNext == -1) {
            return
        }

        val toRetrace = mutableListOf<Pair<ObjectLocation, ObjectStableId>>()

        // Source layer (last that still hasNext): clear the in-flight message so it re-emits, keep epoch + state.
        for (vertexLocation in dag.layers[lastRowWithNext]) {
            val sourceStableId = stableId(vertexLocation)
            val model = activeVertices[sourceStableId]
                ?: continue
            if (model.message != null) {
                model.message = null
                toRetrace.add(vertexLocation to sourceStableId)
            }
        }

        // Strictly-downstream layers: reset epoch + message (re-run, drop previous cycle's message), NOT state.
        for (followingLayer in dag.layers.subList(lastRowWithNext + 1, dag.layers.size)) {
            for (vertexLocation in followingLayer) {
                val downstreamStableId = stableId(vertexLocation)
                val model = activeVertices[downstreamStableId]
                    ?: continue
                if (model.epoch > 0) {
                    model.epoch = 0
                    model.message = null
                    toRetrace.add(vertexLocation to downstreamStableId)
                }
            }
        }

        retrace(toRetrace)
    }


    /**
     * The run is complete: clear every lingering in-flight message but keep each vertex's state (e.g. an
     * accumulating sink's list) and epoch, then re-trace so the client drops the last item's message envelopes
     * and ingress highlighting while displayed results stay. Ported from
     * [tech.kzen.auto.server.objects.flow.FlowExecution.clearMessagesAtEnd].
     */
    private fun clearMessagesAtEnd(matrix: FlowMatrix) {
        val toRetrace = mutableListOf<Pair<ObjectLocation, ObjectStableId>>()
        for (vertexLocation in matrix.verticesByLocation.keys) {
            val vertexStableId = stableId(vertexLocation)
            val model = activeVertices[vertexStableId]
                ?: continue
            if (model.message != null) {
                model.message = null
                toRetrace.add(vertexLocation to vertexStableId)
            }
        }

        retrace(toRetrace)
    }


    // Re-record the trace of each given vertex from its (already mutated) in-memory model, so the client
    // repaints the change without losing the vertex's displayed state. One graph build per call suffices.
    private fun retrace(toRetrace: List<Pair<ObjectLocation, ObjectStableId>>) {
        if (toRetrace.isEmpty()) {
            return
        }

        val graphInstance = GraphCreator.createGraph(
            graphDefinition.filterTransitive(documentPath), graphEnvironment)
        for ((vertexLocation, vertexStableId) in toRetrace) {
            val instance = graphInstance[vertexLocation]
                ?: continue
            traceVertex(vertexStableId, instance, running = false)
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


    private fun createInstance(vertexLocation: ObjectLocation): ObjectInstance {
        // A fresh instance per execution gives clean injected channels (MutableInput / output buffer); the
        // persistent runtime lives in the ActiveVertexModel, not the instance.
        val graphInstance = GraphCreator.createGraph(
            graphDefinition.filterTransitive(documentPath), graphEnvironment)
        return graphInstance[vertexLocation]
            ?: throw IllegalStateException("Vertex not found: $vertexLocation")
    }


    /**
     * Routing snapshot for [FlowUtils.next]. It reads only message-presence, hasNext, epoch and running —
     * never the inspected message/state. Absent vertices route as [VisualVertexModel.empty] (pending, epoch 0).
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

        // The vertex's stable id is the emit address; the controller's trace bridge routes it to the per-vertex
        // trace path (LogicTracePath.ofObjectStableId), matching LogicTraceStore's per-element keying.
        execution.emit(
            Address.of(stableId.value),
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
