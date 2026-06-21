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
import tech.kzen.auto.server.objects.flow.vertex.RunLogicVertex
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.logic.*
import tech.kzen.lib.common.exec.logic.model.*
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
    private val logicHandleFacade: LogicHandleFacade,
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

    // Paused child executions of RunLogicVertex steps (keyed by vertex stable id), cached across
    // pause/resume so a Step Into can descend and a later step resumes the same sub-logic.
    private val pausedChildren = mutableMapOf<ObjectStableId, LogicExecutionFacade>()

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
                    // Run complete (nothing routable, no stream/batch remainder): clear the last item's
                    // lingering in-flight messages — but keep displayed state (e.g. Display's list) — so
                    // the finished graph shows results without stale message envelopes / ingress highlight.
                    clearMessagesAtEnd(matrix, graphDefinition)
                    return LogicResultSuccess(outputTuple())
                }

                // A source still has buffered items (stream/batch): clear the in-flight layer + reset
                // downstream so the next iteration re-flows, then re-select.
                clearIterationForLoop(dag, graphDefinition)
                next = FlowUtils.next(matrix, dag, snapshotVisual(matrix))
                if (next == null) {
                    clearMessagesAtEnd(matrix, graphDefinition)
                    return LogicResultSuccess(outputTuple())
                }
            }

            val command = logicControl.pollCommand()
            if (command == LogicCommand.Cancel) {
                return LogicResultCancelled
            }
            // suppressPause / inStepOutRegion: a Flow that is itself a child being Stepped Over or
            // Stepped Out of runs every vertex to completion instead of pausing per-vertex.
            else if (!executeNextIfPaused && command == LogicCommand.Pause &&
                    ! logicControl.suppressPause() && ! logicControl.inStepOutRegion()) {
                return LogicResultPaused
            }
            executeNextIfPaused = false

            val nextStableId = stableId(next)
            val instance = createInstance(next, graphDefinition)
            seedIfAbsent(nextStableId, instance)

            traceVertex(next, nextStableId, instance, running = true)

            // RunLogicVertex invokes another Logic as a child frame (steppable — see runChildLogic);
            // it can pause/resume, unlike the synchronous runOneVertex path used by every other vertex.
            val runLogicVertex = instance.reference
            if (runLogicVertex is RunLogicVertex) {
                when (val childResult =
                    runChildLogic(next, nextStableId, instance, runLogicVertex, matrix, graphDefinition, logicControl)
                ) {
                    LogicResultPaused -> {
                        // Keep the vertex "running"; its message is still unset, so FlowUtils.next
                        // re-selects it next pass and runChildLogic resumes the cached child.
                        return LogicResultPaused
                    }

                    LogicResultCancelled ->
                        return LogicResultCancelled

                    is LogicResultFailed -> {
                        activeVertices[nextStableId]?.error = childResult.message
                        traceVertex(next, nextStableId, instance, running = false)
                        return if (logicControl.pauseOnError()) LogicResultPaused else childResult
                    }

                    is LogicResultSuccess -> {
                        traceVertex(next, nextStableId, instance, running = false)
                        continue
                    }
                }
            }

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
        for (child in pausedChildren.values) {
            try {
                child.close()
            }
            catch (t: Throwable) {
                logger.warn("Child close error", t)
            }
        }
        pausedChildren.clear()
        logger.info("{} - close - {}", documentPath, error)
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Invoke a [RunLogicVertex]'s target Logic as a child frame — the Flow analogue of [RunStep]
     * [tech.kzen.auto.server.objects.script.step.control.RunStep]. On a fresh descent the single upstream
     * input is passed as the callee's first declared parameter; the call is bracketed by enter/exitFrame
     * (so Step Out can run a frame by depth) and, under Step Over, wrapped in suppressPause so the child
     * runs to completion. A paused child is cached in [pausedChildren] and resumed on the next pass; on
     * success the callee's main result becomes the vertex message.
     */
    private fun runChildLogic(
        vertexLocation: ObjectLocation,
        stableId: ObjectStableId,
        @Suppress("UNUSED_PARAMETER") instance: ObjectInstance,
        vertex: RunLogicVertex,
        matrix: FlowMatrix,
        graphDefinition: GraphDefinition,
        logicControl: LogicControl
    ): LogicResult {
        val activeVertexModel = activeVertices[stableId]!!

        val existing = pausedChildren[stableId]
        val stepOverChild = logicControl.stepOverActive() && existing == null

        val child =
            if (existing != null) {
                existing
            }
            else {
                val argumentMessage = singleInputMessage(vertexLocation, matrix)
                val parameterName = calleeFirstParameterName(vertex.instructions, graphDefinition)

                val created = logicHandleFacade.start(vertex.instructions)
                val argumentValue =
                    if (parameterName != null) {
                        TupleValue(listOf(TupleComponentValue(parameterName, argumentMessage)))
                    }
                    else {
                        TupleValue.empty
                    }

                val ready = created.beforeStart(argumentValue)
                if (! ready) {
                    created.close()
                    return LogicResultFailed("Unable to initialize ${vertex.instructions}")
                }

                // Mirror RunStep: consume the per-tick budget on a fresh descent so a Step Into pauses
                // *before* the callee's first step. No-op during a full run / Step Over (the child runs
                // free below regardless).
                logicControl.consumeStepBudget()

                created
            }

        val result =
            try {
                logicControl.enterFrame()
                try {
                    if (stepOverChild) {
                        logicControl.pushSuppressPause()
                        try {
                            child.continueOrStart(graphDefinition)
                        }
                        finally {
                            logicControl.popSuppressPause()
                        }
                    }
                    else {
                        child.continueOrStart(graphDefinition)
                    }
                }
                finally {
                    logicControl.exitFrame()
                }
            }
            catch (t: Throwable) {
                pausedChildren.remove(stableId)
                child.close()
                logger.warn("Run-logic vertex error - {}", vertexLocation, t)
                return LogicResultFailed(ExceptionUtils.message(t))
            }

        when (result) {
            LogicResultPaused ->
                pausedChildren[stableId] = child

            is LogicResultSuccess -> {
                activeVertexModel.message = result.value.mainComponentValue()
                activeVertexModel.epoch++
                pausedChildren.remove(stableId)
                child.close()
            }

            else -> {
                pausedChildren.remove(stableId)
                child.close()
            }
        }

        return result
    }


    // The single upstream input message for a RunLogicVertex (its sole wired input), used as the
    // callee's argument. Null when nothing is wired / no message has arrived yet.
    private fun singleInputMessage(vertexLocation: ObjectLocation, matrix: FlowMatrix): Any? {
        val vertexDescriptor = matrix.verticesByLocation[vertexLocation]
            ?: return null
        val inputAttribute = vertexDescriptor.inputNames.firstOrNull()
            ?: return null
        val sourceVertex = matrix.traceVertexBackFrom(vertexDescriptor, inputAttribute)
            ?: return null
        return activeVertices[stableId(sourceVertex.objectLocation)]?.message
    }


    // The name of the callee Logic's first declared parameter (input), so the single Flow input maps to
    // it by name (general for Script / Flow callees). Null when the callee declares no parameters.
    private fun calleeFirstParameterName(
        instructions: ObjectLocation,
        graphDefinition: GraphDefinition
    ): TupleComponentName? {
        val calleeGraph = graphCreator.createGraph(
            graphDefinition.filterTransitive(instructions.documentPath), environment)
        val calleeLogic = calleeGraph[instructions]?.reference as? Logic
            ?: return null
        return calleeLogic.define().inputs.components.firstOrNull()?.name
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

        // Output (sink) vertices have no output channel: capture the single upstream input as this
        // vertex's message, which continueOrStart then harvests into the run's result tuple.
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


    /**
     * Start the next loop iteration when a source still has buffered stream/batch items: clear the
     * messages on the last layer that still hasNext (so it re-emits) and reset epochs on the layers
     * below it (so they re-run). Ports ActiveDataflowRepository.clearIteration's "lastRowWithNextMessage
     * != -1" branch; its "-1" (end-of-run) branch is [clearMessagesAtEnd], called before the terminal
     * LogicResultSuccess in [continueOrStart].
     */
    private fun clearIterationForLoop(dag: FlowDag, graphDefinition: GraphDefinition) {
        val lastRowWithNext = dag.layers.indexOfLast { layer ->
            layer.any { activeVertices[stableId(it)]?.hasNext() ?: false }
        }
        if (lastRowWithNext == -1) {
            return
        }

        // Each reset vertex is re-traced (below) from its now-mutated in-memory model, so the client
        // repaints the change WITHOUT losing the vertex's displayed state. Ports ActiveDataflowRepository,
        // whose in-memory ActiveVertexModel was reset (message / epoch, NOT state) and served directly; a
        // logicTraceHandle.clearAll here would drop state too (a reported regression).
        val toRetrace = mutableListOf<Pair<ObjectLocation, ObjectStableId>>()

        // Source layer (last that still hasNext): clear the in-flight message so it re-emits, but keep
        // epoch + state. Re-tracing it is what stops a just-finished item's message from lingering on the
        // source and looking like it's still flowing forward while the source waits to emit the next item.
        for (vertexLocation in dag.layers[lastRowWithNext]) {
            val sourceStableId = stableId(vertexLocation)
            val model = activeVertices[sourceStableId]
                ?: continue
            if (model.message != null) {
                model.message = null
                toRetrace.add(vertexLocation to sourceStableId)
            }
        }

        // Strictly-downstream layers: reset epoch + message (so they re-run and drop the previous cycle's
        // in-flight message), but deliberately NOT state — an accumulating vertex (e.g. Display's running
        // list) keeps it.
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

        retrace(toRetrace, graphDefinition)
    }


    /**
     * The run is complete (nothing routable, no source hasNext): clear every lingering in-flight message
     * but keep each vertex's state (e.g. Display's accumulated list) and epoch, then re-trace so the
     * client drops the last item's message envelopes and ingress highlighting while displayed results
     * stay. Ports ActiveDataflowRepository.clearIteration's "lastRowWithNextMessage == -1" (end-of-run)
     * branch, which the Flow migration had dropped (left the final messages stuck on screen).
     */
    private fun clearMessagesAtEnd(matrix: FlowMatrix, graphDefinition: GraphDefinition) {
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

        retrace(toRetrace, graphDefinition)
    }


    /**
     * Re-record the trace of each given vertex from its (already mutated) in-memory model, so the client
     * repaints the change without losing the vertex's displayed state — what a logicTraceHandle.clearAll
     * would wipe. One graph build per call (lazy, reused) suffices: inspectState is pure in its argument.
     */
    private fun retrace(
        toRetrace: List<Pair<ObjectLocation, ObjectStableId>>,
        graphDefinition: GraphDefinition
    ) {
        if (toRetrace.isEmpty()) {
            return
        }

        val graphInstance = graphCreator.createGraph(
            graphDefinition.filterTransitive(documentPath), environment)
        for ((vertexLocation, vertexStableId) in toRetrace) {
            val instance = graphInstance[vertexLocation]
                ?: continue
            traceVertex(vertexLocation, vertexStableId, instance, running = false)
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
