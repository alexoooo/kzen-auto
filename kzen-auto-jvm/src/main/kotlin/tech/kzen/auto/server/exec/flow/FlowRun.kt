package tech.kzen.auto.server.exec.flow

import tech.kzen.auto.common.objects.document.logic.context.ContextCallBinding
import tech.kzen.auto.common.objects.document.logic.context.LogicContextConventions
import tech.kzen.auto.common.paradigm.flow.api.FlowLogicHost
import tech.kzen.auto.common.paradigm.flow.api.FlowRunInput
import tech.kzen.auto.common.paradigm.flow.api.FlowRunOutput
import tech.kzen.auto.common.paradigm.flow.api.FlowVertex
import tech.kzen.auto.common.paradigm.flow.api.StreamFlowVertex
import tech.kzen.auto.common.paradigm.flow.model.channel.FlowOutputKind
import tech.kzen.auto.common.paradigm.flow.model.channel.MutableFlowOutput
import tech.kzen.auto.common.paradigm.flow.model.channel.MutableInput
import tech.kzen.auto.common.paradigm.flow.model.exec.ActiveVertexModel
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualFlowModel
import tech.kzen.auto.common.paradigm.flow.model.exec.VisualVertexModel
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowDag
import tech.kzen.auto.common.paradigm.flow.model.structure.FlowMatrix
import tech.kzen.auto.common.paradigm.flow.util.FlowUtils
import tech.kzen.auto.common.util.TraceDisplay
import tech.kzen.auto.server.exec.ContextCallSite
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.LogicFailure
import tech.kzen.lib.common.exec.engine.context.InitialBinding
import tech.kzen.lib.common.exec.engine.restoredAs
import tech.kzen.lib.common.exec.tuple.TupleComponentName
import tech.kzen.lib.common.exec.tuple.TupleComponentValue
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.instance.ObjectInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.common.util.ExceptionUtils
import tech.kzen.lib.platform.collect.toPersistentMap


/**
 * One run of a [FlowLogic]'s dataflow DAG on the engine. Position lives on this coroutine's stack, so a vertex
 * is a step boundary ([Execution.checkpoint] before running it) and the engine drives pause / step / cancel
 * centrally; a [FlowLogicHost] vertex's callee is hosted via [Execution.host], its suspension held on the host
 * call's frame.
 *
 * Vertices are dispatched by the capability interfaces they implement ([FlowLogicHost], [FlowRunInput],
 * [FlowRunOutput]), never by concrete class, so a third-party vertex can seed from run arguments, host a child
 * Logic, or contribute to the result tuple without any edit here.
 *
 * Per vertex: inputs are populated from upstream messages, batch/stream output is drained across iterations, a
 * loop iteration is cleared to re-run downstream, and a [VisualVertexModel] is emitted with [Execution.emit] at
 * the vertex's stable-id address for the client to rebuild its visual model from (the controller's trace bridge
 * routes that address to the per-vertex trace path).
 *
 * Pause-on-error (logic-spec §4) is wired via [Execution.recoverable] around each vertex: a vertex failure is
 * rendered (error + trace) and, when pause-on-error is enabled, parks the vertex Suspended(Error) for fix +
 * resume (re-running it on resume) rather than failing the run; with the toggle off the failure propagates.
 *
 * Live-edit migration (logic-spec §5): the per-vertex progress ([activeVertices]) and harvested output
 * ([outputAccumulator]) are carried across a pause -> edit -> resume by the Flow root node's
 * [Execution.onCapture] / [Execution.restored] as a [FlowMigrationState] (keyed by stable id), so the rebuilt
 * run continues from the live frontier instead of restarting.
 */
class FlowRun(
    private val execution: Execution,
    private val documentPath: DocumentPath,
    private val graphDefinition: GraphDefinition,
    private val childLogics: Map<ObjectStableId, FlowChildLogic>,
    private val objectStableMapper: ObjectStableMapper,
    private val graphEnvironment: GraphEnvironment
) {
    //-----------------------------------------------------------------------------------------------------------------
    private companion object {
        // A checkpoint that took longer than this to return means the engine paused/stepped at it — always
        // trace those (stepping fidelity). Below it we are free-running, so per-vertex traces are throttled.
        const val steppingGapNanos = 50_000_000L      // 50 ms

        // During free-running, emit at most one trace per vertex per this window.
        const val traceThrottleNanos = 100_000_000L   // 100 ms
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Per-vertex runtime, keyed by stable id so it survives renames; output vertices' harvested values.
    private val activeVertices = mutableMapOf<ObjectStableId, ActiveVertexModel>()
    private val outputAccumulator = mutableMapOf<TupleComponentName, Any?>()

    // Per-vertex call-site context declarations, read from notation once per location — a host vertex inside a
    // stream/batch loop is re-visited every iteration. Confined to the run coroutine.
    private val callContextsCache = HashMap<ObjectLocation, List<ContextCallBinding>>()

    // Wall-clock nanos of each vertex's last emitted trace, for throttling hot free-running loops.
    private val lastTraceNanos = mutableMapOf<ObjectStableId, Long>()

    // The run's single graph instance — built once in run() and reused for every vertex execution and
    // retrace (a live edit builds a fresh FlowRun, so it stays valid for this run's whole lifetime).
    private lateinit var runInstance: GraphInstance


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun run(): TupleValue {
        // Live-edit migration (logic-spec §5): adopt the predecessor run's DAG progress (read once at start) so
        // the walker continues from the carried frontier, and register the capture so a later edit carries this
        // run's progress forward. Null on a fresh run -> nothing to adopt, the DAG runs from the start.
        execution.restoredAs<FlowMigrationState>()?.let { carried ->
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

        // graphDefinition is immutable for a FlowRun's life (a live edit builds a new FlowRun via
        // migration), so one graph build serves every vertex execution and retrace for the whole run.
        runInstance = GraphCreator.createGraph(
            graphDefinition.filterTransitive(documentPath), graphEnvironment)

        var lastCheckpointReturn: Long? = null

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
            val nextStableId = stableId(next)
            execution.checkpoint(nextStableId)

            // The engine only suspends checkpoint() while paused/stepping, so a large gap since the last
            // return is the observable proxy for "the user is stepping" — always trace those (fidelity),
            // and throttle the µs-apart free-running executions below.
            val checkpointReturn = System.nanoTime()
            val pausedOrStepping =
                lastCheckpointReturn?.let { checkpointReturn - it >= steppingGapNanos } ?: true
            lastCheckpointReturn = checkpointReturn

            val instance = instanceFor(next)
            seedIfAbsent(nextStableId, instance)

            traceVertex(nextStableId, instance, running = true, force = pausedOrStepping)

            val reference = instance.reference

            // A logic-host vertex invokes another Logic as a confined child node (steppable via the engine
            // tree): Step Into descends into it; Step Over / Step Out / Run run it to completion.
            if (reference is FlowLogicHost) {
                runChildVertex(next, nextStableId, instance, matrix)
                val clearedChildError = clearStaleError(nextStableId)
                traceVertex(nextStableId, instance, running = false, force = pausedOrStepping || clearedChildError)
                continue
            }

            // Pause-on-error (logic-spec §4): the engine renders the vertex failure (error + trace) then, if
            // pause-on-error is on, parks the vertex Suspended(Error) for fix + resume and re-runs it on resume;
            // if off, the failure propagates and the run fails.
            execution.recoverable({ t ->
                activeVertices[nextStableId]?.error = ExceptionUtils.message(t)
                traceVertex(nextStableId, instance, running = false, force = true)
            }) {
                runOneVertex(next, nextStableId, instance, matrix)
            }

            val clearedError = clearStaleError(nextStableId)
            traceVertex(nextStableId, instance, running = false, force = pausedOrStepping || clearedError)

            if (reference is FlowRunOutput) {
                outputAccumulator[reference.tupleComponentName] = activeVertices[nextStableId]?.message
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Run a [FlowLogicHost] vertex's pre-compiled callee as a confined child node ([Execution.host]) — the Flow
     * analogue of [RunStep][tech.kzen.auto.server.objects.script.step.control.RunStep]. The callee's parameters
     * are bound per [FlowLogicHost]'s rule (wired inputs positionally, then the `arguments` literals by name,
     * conflicts having been refused at compile); on success the callee's main result becomes the vertex's
     * message (which downstream vertices wire to).
     */
    private suspend fun runChildVertex(
        vertexLocation: ObjectLocation,
        stableId: ObjectStableId,
        instance: ObjectInstance,
        matrix: FlowMatrix
    ) {
        val activeVertexModel = activeVertices[stableId]!!
        val childLogic = childLogics[stableId]
            ?: throw LogicFailure("Logic-host vertex child not compiled: $vertexLocation")

        val inputs = bindChildInputs(
            instance.reference as FlowLogicHost, childLogic, vertexLocation, matrix)

        // Pause-on-error for a hosted child vertex: same recoverable contract as a regular vertex — render the
        // failure, then park Error (fix + resume re-hosts) or propagate. A child cancel always propagates.
        val result = execution.recoverable({ t ->
            activeVertexModel.error = ExceptionUtils.message(t)
            traceVertex(stableId, instance, running = false, force = true)
        }) {
            // The hosting VERTEX is the child's call-site, in both senses the engine cares about. As
            // `callerStableId` it attributes the child's frame to THIS vertex, so two host vertices pointing at
            // the same document stop sharing one frame identity (they did until now — the call passed neither
            // argument). As the source of `initialBindings` it supplies the callee's declared `context.requires`
            // per call, which is what lets one unedited callee run against a different subject from each vertex.
            execution.host(
                childLogic.childStableId, childLogic.logic, inputs, stableId,
                initialBindings = callSiteBindings(vertexLocation))
        }

        activeVertexModel.message = result.mainComponentValue()
        activeVertexModel.epoch++
    }


    /**
     * The hosting vertex's `contexts:` map, resolved against the scope as it stands right now — what this call
     * supplies to the child and no longer.
     *
     * Read from the vertex's NOTATION rather than off the [FlowLogicHost] instance beside `arguments`, because
     * a context declaration is a weak reference and constructor-injecting one that dangles fails the whole
     * vertex object at creation. It also means a live edit needs nothing: the rebuilt run re-reads this and
     * re-supplies from whatever its sources hold NOW, which is exactly the contract a bootstrap value has.
     *
     * Memoized per vertex for the run's life. A Flow re-visits a host vertex once per iteration of an enclosing
     * stream/batch loop, and the map is notation — it cannot change without a migrate rebuilding this FlowRun.
     */
    private fun callSiteBindings(vertexLocation: ObjectLocation): List<InitialBinding> {
        val callBindings = callContextsCache.getOrPut(vertexLocation) {
            LogicContextConventions.stepCallContexts(
                graphDefinition.graphStructure.graphNotation, vertexLocation)
        }

        return ContextCallSite.initialBindings(execution, callBindings)
    }


    private fun bindChildInputs(
        host: FlowLogicHost,
        childLogic: FlowChildLogic,
        vertexLocation: ObjectLocation,
        matrix: FlowMatrix
    ): TupleValue {
        val parameterNames = childLogic.parameterNames

        val positional = wiredInputMessages(vertexLocation, matrix)
            .take(parameterNames.size)
            .mapIndexed { index, message -> TupleComponentValue(parameterNames[index], message) }

        val literals = host.arguments.map { (name, literal) ->
            TupleComponentValue(TupleComponentName(name), literal)
        }

        return TupleValue(positional + literals)
    }


    // Each wired input's upstream message, in the vertex's declared input order — an input with no upstream is
    // skipped rather than contributing a null, so positional binding counts only what is actually connected.
    private fun wiredInputMessages(vertexLocation: ObjectLocation, matrix: FlowMatrix): List<Any?> {
        val vertexDescriptor = matrix.verticesByLocation[vertexLocation]
            ?: return listOf()

        return vertexDescriptor
            .inputNames
            .mapNotNull { matrix.traceVertexBackFrom(vertexDescriptor, it) }
            .map { activeVertices[stableId(it.objectLocation)]?.message }
    }


    // The single upstream input message for a sink vertex (its sole wired input); logic hosts bind via
    // wiredInputMessages instead. Null when nothing is wired / no message has arrived yet.
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
        if (reference is FlowRunInput) {
            activeVertexModel.message = execution.inputs.find(reference.tupleComponentName)
            activeVertexModel.epoch++
            return
        }

        // Output (sink) vertices have no output channel: capture the single upstream input as this vertex's
        // message, which the run loop then harvests into the result tuple.
        if (reference is FlowRunOutput) {
            activeVertexModel.message = singleInputMessage(vertexLocation, matrix)
            activeVertexModel.epoch++
            return
        }

        @Suppress("UNCHECKED_CAST")
        val flowVertex = reference as FlowVertex<Any?>

        populateInputs(instance, vertexLocation, matrix)

        // The instance's output channel is shared across this vertex's executions, so reset it before
        // running: a process() that emitted then threw would otherwise leave a stale item to re-emit.
        (instance.constructorAttributes[FlowUtils.mainOutputAttributeName] as? MutableFlowOutput<*>)?.clear()

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
            // The other half of the RequiredOutput contract (the channel enforces "at most one"). Only the
            // generic process path reaches here, so a capability vertex whose declared output channel is
            // decorative — it returns before process — is exempt, as its channel is never written.
            check(output.kind != FlowOutputKind.Required || !output.bufferIsEmpty()) {
                "Required output of $vertexLocation was not set: a RequiredOutput emits exactly once " +
                        "per execution"
            }

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
     * re-run).
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
     * accumulating sink's list) and epoch, then force a final trace for EVERY vertex — so throttling during
     * free-running can't leave any card on a stale intermediate frame, and the client drops the last item's
     * message envelopes and ingress highlighting while displayed results stay.
     */
    private fun clearMessagesAtEnd(matrix: FlowMatrix) {
        for (vertexLocation in matrix.verticesByLocation.keys) {
            val vertexStableId = stableId(vertexLocation)
            val model = activeVertices[vertexStableId]
                ?: continue
            model.message = null
            val instance = runInstance[vertexLocation]
                ?: continue
            traceVertex(vertexStableId, instance, running = false, force = true)
        }
    }


    // Re-record the trace of each given vertex from its (already mutated) in-memory model, so the client
    // repaints the change without losing the vertex's displayed state. Left throttled (force = false): a
    // loop boundary that force-re-serialized a reset downstream accumulator every iteration would undo the
    // trace bounding — during stepping the wall-clock throttle still lets these through (last emit was long ago).
    private fun retrace(toRetrace: List<Pair<ObjectLocation, ObjectStableId>>) {
        for ((vertexLocation, vertexStableId) in toRetrace) {
            val instance = runInstance[vertexLocation]
                ?: continue
            traceVertex(vertexStableId, instance, running = false, force = false)
        }
    }


    /**
     * A vertex's [Execution.recoverable] block returned normally, so any error still set on it is stale — from a
     * pause-on-error park that was fixed + resumed, or carried in across a live-edit migration. Clearing it is
     * what makes the client's red card subside exactly when the vertex succeeds again; the return value says
     * whether that happened, so the follow-up trace can be forced past the free-running throttle.
     */
    private fun clearStaleError(stableId: ObjectStableId): Boolean {
        val model = activeVertices[stableId]
            ?: return false

        if (model.error == null) {
            return false
        }

        model.error = null
        return true
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


    // The vertex's instance from the run's single graph build. Its injected channels are reset by the runner
    // (inputs via populateInputs' set-or-clear, output via MutableFlowOutput.clear before process); the
    // persistent runtime lives in the ActiveVertexModel, not the instance.
    private fun instanceFor(vertexLocation: ObjectLocation): ObjectInstance {
        return runInstance[vertexLocation]
            ?: throw IllegalStateException("Vertex not found: $vertexLocation")
    }


    /**
     * Routing snapshot for [FlowUtils.next]. It reads only message-presence, hasNext, epoch and running —
     * never the inspected message/state, and never the error (which would make the vertex route as
     * [tech.kzen.auto.common.paradigm.flow.model.exec.VisualVertexPhase.Error], the client-only phase).
     * Absent vertices route as [VisualVertexModel.empty] (pending, epoch 0).
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
                        model.epoch,
                        // Deliberately NOT model.error: routing must never see the Error phase — an errored
                        // vertex stays selectable, and the recoverable re-run is the fix path. Otherwise a
                        // carried-in error (live-edit migration during an error park) would make a multi-vertex
                        // layer skip it forever and the flow would stall instead of re-running it.
                        null)
                }
        }
        return VisualFlowModel(builder.toPersistentMap())
    }


    private fun traceVertex(
        stableId: ObjectStableId,
        instance: ObjectInstance,
        running: Boolean,
        force: Boolean
    ) {
        val model = activeVertices[stableId]
            ?: return

        // Throttle hot free-running loops: skip the (potentially expensive) inspect + emit unless forced
        // (stepping / error / final flush) or this vertex hasn't emitted within the throttle window. The
        // gate is checked BEFORE inspectState so an accumulating sink's O(state) serialization is skipped.
        val now = System.nanoTime()
        if (!force) {
            val last = lastTraceNanos[stableId]
            if (last != null && now - last < traceThrottleNanos) {
                return
            }
        }
        lastTraceNanos[stableId] = now

        // Inspection is non-fatal (a trace must never fail a run the vertex itself survived): fall back to a
        // truncated toString if inspectState / inspectMessage throws.
        val stateValue = model.state?.let { state ->
            try {
                @Suppress("UNCHECKED_CAST")
                (instance.reference as FlowVertex<Any>).inspectState(state)
            }
            catch (e: Exception) {
                truncatedToString(state)
            }
        }

        val messageValue = model.message?.let { message ->
            try {
                @Suppress("UNCHECKED_CAST")
                (instance.reference as FlowVertex<Any?>).inspectMessage(message)
                    ?: ExecutionValue.ofArbitrary(message)
                    ?: truncatedToString(message)
            }
            catch (e: Exception) {
                truncatedToString(message)
            }
        }

        val visualVertexModel = VisualVertexModel(
            running,
            stateValue,
            messageValue,
            model.hasNext(),
            model.epoch,
            model.error)

        // The vertex's stable id is the emit address; the trace query view translates it to the per-vertex
        // trace path (LogicTracePath.ofObjectStableId) — the default stable-id path, no marker routing.
        execution.emit(
            Address.of(stableId.value),
            ExecutionValue.of(VisualVertexModel.toJsonCollection(visualVertexModel)))
    }


    private fun truncatedToString(value: Any): ExecutionValue {
        return ExecutionValue.of(
            TraceDisplay.truncatedToString(value, TraceDisplay.maxFlowTraceChars))
    }


    private fun outputTuple(): TupleValue {
        return TupleValue(
            outputAccumulator.map { TupleComponentValue(it.key, it.value) })
    }


    private fun stableId(objectLocation: ObjectLocation): ObjectStableId {
        return objectStableMapper.objectStableId(objectLocation)
    }
}
