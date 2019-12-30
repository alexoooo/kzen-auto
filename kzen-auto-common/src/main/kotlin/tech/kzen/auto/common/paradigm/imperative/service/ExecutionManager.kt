package tech.kzen.auto.common.paradigm.imperative.service

import kotlinx.coroutines.delay
import tech.kzen.auto.common.paradigm.imperative.api.ScriptControl
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeFrame
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResponse
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeState
import tech.kzen.auto.common.paradigm.imperative.model.control.*
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.persistentListOf
import tech.kzen.lib.platform.collect.persistentMapOf
import tech.kzen.lib.platform.collect.toPersistentMap


class ExecutionManager(
        private val executionInitializer: ExecutionInitializer,
        private val actionExecutor: ActionExecutor
):
        LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation)
        suspend fun onExecutionModel(host: DocumentPath, executionModel: ImperativeModel)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var models: PersistentMap<DocumentPath, ImperativeModel> = persistentMapOf()
    private var controlTrees = mutableMapOf<DocumentPath, BranchControlNode>()


    private suspend fun modelOrInit(host: DocumentPath): ImperativeModel {
        val existing = models[host]
        if (existing != null) {
            return existing
        }
        val initial = executionInitializer.initialExecutionModel(host)
        models = models.put(host, initial)
        publishExecutionModel(host, initial)
        return initial
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun observe(observer: Observer) {
        observers.add(observer)

//        println("!!! observe - onExecutionModel - $models")
        for (model in models) {
            observer.onExecutionModel(model.key, model.value)
        }
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    private suspend fun publishBeforeExecution(
            host: DocumentPath,
            objectLocation: ObjectLocation
    ) {
//        println("^^^ publishBeforeExecution - $host - $objectLocation")
        for (subscriber in observers) {
            subscriber.beforeExecution(host, objectLocation)
        }
    }


    private suspend fun publishExecutionModel(
            host: DocumentPath,
            model: ImperativeModel
    ) {
//        val model = models[host]!!

//        val current = version++
//        println("^^^ publishExecutionModel - $current - $host - $model")
        for (subscriber in observers) {
//            println("^^^ ### publishExecutionModel - to subscriber - $current")
            subscriber.onExecutionModel(host, model)
        }
    }


    override suspend fun onCommandSuccess(
            event: NotationEvent, graphDefinition: GraphDefinitionAttempt
    ) {
        val graphStructure = graphDefinition.successful.graphStructure

        for (host in models.keys) {
            val newModels = apply(host, event, graphStructure)

            if (models != newModels) {
                controlTrees[host] = ControlTree.readSteps(graphStructure, host)

                models = newModels
                if (host in models) {
                    publishExecutionModel(host, models[host]!!)
                }
            }
        }
    }


    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}

    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {}


    private fun apply(
            documentPath: DocumentPath,
            event: NotationEvent,
            graphStructure: GraphStructure
    ): PersistentMap<DocumentPath, ImperativeModel> {
        return when (event) {
            is SingularNotationEvent ->
                applySingular(documentPath, models, event, graphStructure)

            is CompoundNotationEvent -> {
                applyCompound(documentPath, event ,graphStructure)
            }
        }
    }


    private fun applyCompound(
            documentPath: DocumentPath,
            event: CompoundNotationEvent,
            graphStructure: GraphStructure
    ): PersistentMap<DocumentPath, ImperativeModel> {
        val model = models[documentPath]!!
        val appliedWithDependentEvents = applyCompoundWithDependentEvents(
                documentPath, model, event)
        if (models != appliedWithDependentEvents) {
            return appliedWithDependentEvents
        }

        var builder = models
        for (singularEvent in event.singularEvents) {
            builder = applySingular(documentPath, builder, singularEvent, graphStructure)
        }
        return builder
    }


    private fun applyCompoundWithDependentEvents(
            documentPath: DocumentPath,
            model: ImperativeModel,
            event: CompoundNotationEvent
    ): PersistentMap<DocumentPath, ImperativeModel> {
        return when (event) {
            is RenamedDocumentRefactorEvent -> {
//                println("^^^^^ applyCompoundWithDependentEvents - $documentPath - $event")
                if (event.removedUnderOldName.documentPath == documentPath) {
                    val newModel = model.move(
                            event.removedUnderOldName.documentPath, event.createdWithNewName.destination)

                    val removed = models.remove(event.removedUnderOldName.documentPath)
                    removed.put(event.createdWithNewName.destination, newModel)
                }
                else {
                    models
                }
            }

            else ->
                models
        }
    }


    private fun applySingular(
            documentPath: DocumentPath,
            currentModels: PersistentMap<DocumentPath, ImperativeModel>,
            event: SingularNotationEvent,
            graphStructure: GraphStructure
    ): PersistentMap<DocumentPath, ImperativeModel> {
        val model = currentModels[documentPath]!!

        return when (event) {
            is RemovedObjectEvent ->
                currentModels.put(documentPath,
                        model.remove(event.objectLocation))

            is RenamedObjectEvent ->
                currentModels.put(documentPath,
                        model.rename(event.objectLocation, event.newName))

            is AddedObjectEvent -> {
                val initialState = initialState(event.objectLocation, graphStructure)

                currentModels.put(documentPath,
                        model.add(event.objectLocation, initialState))
            }

            is DeletedDocumentEvent ->
                if (event.documentPath == documentPath) {
                    currentModels.remove(documentPath)
                }
                else {
                    currentModels
                }

            else ->
                currentModels
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    suspend fun isExecuting(
//            host: DocumentPath
//    ): Boolean {
//        return modelOrInit(host).containsStatus(ImperativePhase.Running)
//    }


    suspend fun executionModel(
            host: DocumentPath
    ): ImperativeModel {
        return modelOrInit(host)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun reset(
            host: DocumentPath
    ): Digest {
        val model = ImperativeModel(persistentListOf())
        models = models.put(host, model)

        publishExecutionModel(host, model)

        return model.digest()
    }


    suspend fun start(
            host: DocumentPath,
            graphStructure: GraphStructure
    ): Digest {
        val controlTree = ControlTree.readSteps(graphStructure, host)
        controlTrees[host] = controlTree

        val initialState = initializeFrame(controlTree)

        val frame = ImperativeFrame(host, initialState.toPersistentMap())
        val executionModel = ImperativeModel(persistentListOf(frame))

        models = models.put(host, executionModel)

        val model = modelOrInit(host)
        publishExecutionModel(host, model)
        return model.digest()
    }


    private fun initializeFrame(
            controlTree: BranchControlNode
    ): PersistentMap<ObjectPath, ImperativeState> {
        val values = mutableMapOf<ObjectPath, ImperativeState>()

        controlTree.traverseDepthFirstNodes { node ->
            values[node.step.objectPath] = initialState(node)
        }

        return values.toPersistentMap()
    }


    private fun initialState(
            controlNode: StepControlNode
    ): ImperativeState {
        return if (controlNode.branches.isNotEmpty()) {
            ImperativeState.initialControlFlow
        }
        else {
            ImperativeState.initialSingular
        }
    }


    private fun initialState(
            objectLocation: ObjectLocation,
            graphStructure: GraphStructure
    ): ImperativeState {
        val isControlFlow = isControlFlow(objectLocation, graphStructure)

        return if (isControlFlow) {
            ImperativeState.initialControlFlow
        }
        else {
            ImperativeState.initialSingular
        }
    }


    private fun isControlFlow(
            objectLocation: ObjectLocation,
            graphStructure: GraphStructure
    ): Boolean {
        val inheritanceChain = graphStructure.graphNotation.inheritanceChain(objectLocation)
        return inheritanceChain.any { it.objectPath.name == ScriptControl.objectName }
    }



    //-----------------------------------------------------------------------------------------------------------------
    suspend fun execute(
            host: DocumentPath,
            objectLocation: ObjectLocation,
            delayMillis: Int = 0
    ): ImperativeResponse {
        if (delayMillis > 0) {
//            println("ExecutionManager | %%%% delay($delayMillis)")
            delay(delayMillis.toLong())
        }
        willExecute(host, objectLocation)

        if (delayMillis > 0) {
//            println("ExecutionManager | delay($delayMillis)")
            delay(delayMillis.toLong())
        }

        val imperativeModel = executionModel(host)

        val state = imperativeModel.frames.last().states[objectLocation.objectPath]!!

        if (state.controlState == null || state.controlState == FinalControlState) {
            val result = actionExecutor.execute(objectLocation, imperativeModel)

            val executionState = ImperativeState(
                    false,
                    result,
                    null
            )

            val digest = modelOrInit(host).digest()
            didExecute(host, objectLocation, executionState, null)
            return ImperativeResponse(result, null, digest)
        }
        else {
            @Suppress("MoveVariableDeclarationIntoWhen")
            val controlTransition = actionExecutor.control(objectLocation, imperativeModel)

            val controlState = when (controlTransition) {
                EvaluateControlTransition ->
                    FinalControlState

                is InternalControlTransition ->
                    InternalControlState(controlTransition.branchIndex, controlTransition.value)
            }

            val executionState = ImperativeState(
                    false,
                    state.previous,
                    controlState
            )

            val branchReset =
                    (controlState as? InternalControlState)?.branchIndex

            didExecute(host, objectLocation, executionState, branchReset)

            val digest = modelOrInit(host).digest()
            return ImperativeResponse(null, controlTransition, digest)
        }
    }


    private suspend fun willExecute(
            host: DocumentPath,
            objectLocation: ObjectLocation
    ) {
        val model = modelOrInit(host)
        val existingFrame = model.findLast(objectLocation)

        val upsertFrame = existingFrame
                ?: model.frames.last()

        val state = upsertFrame.states[objectLocation.objectPath]
                ?: return

        val updatedFrame = upsertFrame.set(
                objectLocation.objectPath,
                state.copy(running = true))

        val updatedModel = ImperativeModel(
                model.frames.set(model.frames.size - 1, updatedFrame))

        models = models.put(host, updatedModel)
//        upsertFrame.states[objectLocation.objectPath] = state.copy(running = true)

        publishExecutionModel(host, updatedModel)
        publishBeforeExecution(host, objectLocation)
    }


    private suspend fun didExecute(
            host: DocumentPath,
            objectLocation: ObjectLocation,
            executionState: ImperativeState,
            branchReset: Int?
    ) {
        val model = modelOrInit(host)
        val existingFrame = model.findLast(objectLocation)

        val upsertFrame = existingFrame
                ?: model.frames.last()

        val updatedFrame = upsertFrame.set(
                objectLocation.objectPath,
                executionState)

        val clearedFrame =
                if (branchReset != null) {
                    val controlTree = controlTrees[host]!!
                    val controlNode = controlTree.find(objectLocation)!!
                    val branch = controlNode.branches[branchReset]

                    var buffer = updatedFrame.states
                    branch.traverseDepthFirstNodes { node ->
                        val state = initialState(node)
                        buffer = buffer.put(node.step.objectPath, state)
                    }
                    updatedFrame.copy(states = buffer)
                }
                else {
                    updatedFrame
                }

        val updatedModel = ImperativeModel(
                model.frames.set(model.frames.size - 1, clearedFrame))

        models = models.put(host, updatedModel)

//        println("^^^ didExecute: $host - $updatedModel")
        publishExecutionModel(host, updatedModel)
    }
}
