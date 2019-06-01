package tech.kzen.auto.common.paradigm.dataflow.service.active

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.dataflow.api.Dataflow
import tech.kzen.auto.common.paradigm.dataflow.api.StreamDataflow
import tech.kzen.auto.common.paradigm.dataflow.model.channel.MutableDataflowOutput
import tech.kzen.auto.common.paradigm.dataflow.model.channel.MutableRequiredInput
import tech.kzen.auto.common.paradigm.dataflow.model.exec.*
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowDag
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowMatrix
import tech.kzen.auto.common.paradigm.dataflow.service.format.DataflowMessageInspector
import tech.kzen.auto.common.paradigm.dataflow.util.DataflowUtils
import tech.kzen.auto.common.service.GraphInstanceManager
import tech.kzen.auto.common.service.GraphStructureManager
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.edit.*
import tech.kzen.lib.platform.collect.toPersistentMap


class ActiveDataflowManager(
        private val instanceManager: GraphInstanceManager,
        private val dataflowMessageInspector: DataflowMessageInspector,
        private val graphStructureManager: GraphStructureManager
) :
        GraphStructureManager.Observer
{
//    //-----------------------------------------------------------------------------------------------------------------
//    private class Handle(
//            val input: ObjectLocation?,
//            val output: ObjectLocation?
//    )


    //-----------------------------------------------------------------------------------------------------------------
    private var models: MutableMap<DocumentPath, ActiveDataflowModel> = mutableMapOf()


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(
            graphStructure: GraphStructure,
            event: NotationEvent?
    ) {
        if (event == null) {
            return
        }

        // NB: avoid concurrent modification for DeletedDocumentEvent handling
        val modelHosts = models.keys.toList()

        for (host in modelHosts) {
            apply(host, event)
        }
    }


    private suspend fun apply(
            documentPath: DocumentPath,
            event: NotationEvent
    ) {
        return when (event) {
            is SingularNotationEvent ->
                applySingular(documentPath, event)

            is CompoundNotationEvent -> {
                applyCompound(documentPath, event)
            }
        }
    }


    private suspend fun applySingular(
            documentPath: DocumentPath,
            event: SingularNotationEvent
    ) {
        val model = models[documentPath]!!

        when (event) {
            is RemovedObjectEvent -> {
                if (event.objectLocation.documentPath != documentPath) {
                    return
                }

                model.vertices.remove(event.objectLocation)
            }

            is RenamedObjectEvent -> {
                if (event.objectLocation.documentPath != documentPath) {
                    return
                }

                val activeVertexModel = model.vertices.remove(event.objectLocation)!!
                val newObjectPath = event.objectLocation.objectPath.copy(name = event.newName)
                val newLocation = ObjectLocation(documentPath, newObjectPath)
                model.vertices[newLocation] = activeVertexModel
            }

            is AddedObjectEvent -> {
                if (event.objectLocation.documentPath != documentPath) {
                    return
                }

                val initialVertexModel = initializeVertex(event.objectLocation)
                model.vertices[event.objectLocation] = initialVertexModel
            }

            is DeletedDocumentEvent -> {
                if (event.documentPath != documentPath) {
                    return
                }

                models.remove(event.documentPath)
            }

            else ->
                Unit
        }
    }


    private suspend fun applyCompound(
            documentPath: DocumentPath,
            event: CompoundNotationEvent
    ) {
        val appliedWithDependentEvents = applyCompoundWithDependentEvents(
                documentPath, event)
        if (appliedWithDependentEvents) {
            return
        }

        for (singularEvent in event.singularEvents) {
             applySingular(documentPath, singularEvent)
        }
    }


    private fun applyCompoundWithDependentEvents(
            documentPath: DocumentPath,
            event: CompoundNotationEvent
    ): Boolean {
        val model = models[documentPath]!!

        return when (event) {
            is RenamedDocumentRefactorEvent -> {
//                println("^^^^^ applyCompoundWithDependentEvents - $documentPath - $event")
                if (event.removedUnderOldName.documentPath == documentPath) {
//                    model.

                    model.move(
                            event.removedUnderOldName.documentPath, event.createdWithNewName.destination)

                    models.remove(event.removedUnderOldName.documentPath)
                    models[event.createdWithNewName.destination] = model

                    true
                }
                else {
                    false
                }
            }

            else ->
                false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun getOrInit(
            host: DocumentPath
    ): ActiveDataflowModel {
        models[host]?.let {
            return it
        }

        val serverGraphStructure = graphStructureManager.serverGraphStructure()

        check(host in serverGraphStructure.graphNotation.documents)

        val vertexMatrix = DataflowMatrix.ofQueryDocument(host, serverGraphStructure.graphNotation)

        val builder = mutableMapOf<ObjectLocation, ActiveVertexModel>()
        for (vertexLocation in vertexMatrix.verticesByLocation().keys) {
            builder[vertexLocation] = initializeVertex(vertexLocation)
        }

//        val dataflowDag = DataflowDag.of(vertexMatrix)

        val activeDataflowModel = ActiveDataflowModel(builder/*, dataflowDag*/)
        models[host] = activeDataflowModel
        return activeDataflowModel
    }


    private suspend fun initializeVertex(
            vertexLocation: ObjectLocation
    ): ActiveVertexModel {
        val vertexInstance = instanceManager.get(vertexLocation).reference as Dataflow<*>

        val initialState = vertexInstance.initialState()
        val initialStateOrNull =
                if (initialState == Unit) {
                    null
                }
                else {
                    initialState
                }

        return ActiveVertexModel(
                initialStateOrNull,
                null,
                mutableListOf(),
                false,
                0,
                null)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun inspect(
            host: DocumentPath
    ): VisualDataflowModel {
        val activeDataflowModel = getOrInit(host)

        val builder = mutableMapOf<ObjectLocation, VisualVertexModel>()

        for ((vertexLocation, activeVertexModel) in activeDataflowModel.vertices) {
            builder[vertexLocation] = inspectActiveVertexModel(
                    vertexLocation, activeVertexModel)
        }

        return VisualDataflowModel(
                builder.toPersistentMap())
    }


    suspend fun inspectVertex(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ): VisualVertexModel {
        val activeDataflowModel = getOrInit(host)
        val activeVertexModel = activeDataflowModel.vertices[vertexLocation]
                ?: initializeVertex(vertexLocation)

        return inspectActiveVertexModel(
                vertexLocation, activeVertexModel)
    }


    private suspend fun inspectActiveVertexModel(
            vertexLocation: ObjectLocation,
            activeVertexModel: ActiveVertexModel
    ): VisualVertexModel {
        val stateInspection = activeVertexModel.state?.let {
            inspectState(vertexLocation, it)
        }

        val messageInspection = activeVertexModel.message
                ?.let(dataflowMessageInspector::inspectMessage)

        val hasNext = activeVertexModel.streamHasNext ||
                activeVertexModel.remainingBatch.isNotEmpty()

        return VisualVertexModel(
                false,
                stateInspection,
                messageInspection,
                hasNext,
                activeVertexModel.epoch.toInt(),
                activeVertexModel.error)
    }


    private suspend fun inspectState(
            vertexLocation: ObjectLocation,
            state: Any
    ): ExecutionValue {
        @Suppress("UNCHECKED_CAST")
        val dataflow = instanceManager.get(vertexLocation).reference as Dataflow<Any>
        return dataflow.inspectState(state)
    }


    suspend fun reset(
            host: DocumentPath
    ): VisualDataflowModel {
        models.remove(host)
        return inspect(host)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun executeVisual(
            host: DocumentPath,
            vertexLocation: ObjectLocation
    ): VisualVertexTransition {
        val activeDataflowModel = getOrInit(host)
        val activeVertexModel = activeDataflowModel.vertices[vertexLocation]!!

        val previousStateView = activeVertexModel.state?.let {
            inspectState(vertexLocation, it)
        }

        val loop = mutableListOf<ObjectLocation>()
        val cleared = mutableListOf<ObjectLocation>()

        try {
            execute(host,
                    vertexLocation,
                    { loop.add(it) },
                    { cleared.add(it) })
        }
        catch (e: Throwable) {
            return VisualVertexTransition(
                    null,
                    null,
                    activeVertexModel.hasNext(),
                    activeVertexModel.epoch.toInt(),
                    loop,
                    cleared,
                    e.message ?: "Error")
        }

        val nextStateView = activeVertexModel.state?.let {
            inspectState(vertexLocation, it)
        }

        val stateChange =
                if (previousStateView != nextStateView) {
                    nextStateView
                }
                else {
                    null
                }

        val messageView = activeVertexModel.message?.let {
            dataflowMessageInspector.inspectMessage(it)
        }

        return VisualVertexTransition(
                stateChange,
                messageView,
                activeVertexModel.hasNext(),
                activeVertexModel.epoch.toInt(),
                loop,
                cleared,
                null)
    }


    suspend fun execute(
            host: DocumentPath,
            vertexLocation: ObjectLocation,
            loopConsumer: (ObjectLocation) -> Unit = {},
            clearedConsumer: (ObjectLocation) -> Unit = {}
    ) {
        val serverGraphStructure = graphStructureManager.serverGraphStructure()
        val vertexMatrix = DataflowMatrix.ofQueryDocument(host, serverGraphStructure.graphNotation)
        val dataflowDag = DataflowDag.of(vertexMatrix)

        val activeDataflowModel = getOrInit(host)
        executeDirect(activeDataflowModel, vertexLocation, dataflowDag)

        val visualDataflowModel = inspect(host)

        if (isDone(dataflowDag, visualDataflowModel)) {
            clearIteration(dataflowDag, activeDataflowModel, loopConsumer, clearedConsumer)
        }
    }


    private fun isDone(
            dataflowDag: DataflowDag,
            visualDataflowModel: VisualDataflowModel
    ): Boolean {
        val next = DataflowUtils.next(dataflowDag, visualDataflowModel)
        return next == null
    }


    private fun clearIteration(
            dataflowDag: DataflowDag,
            activeDataflowModel: ActiveDataflowModel,
            loopConsumer: (ObjectLocation) -> Unit,
            clearedConsumer: (ObjectLocation) -> Unit
    ) {
        val lastRowWithNextMessage = dataflowDag
                .layers
                .indexOfLast {layer ->
                    layer.any {
                        activeDataflowModel
                                .vertices[it]
                                ?.hasNext()
                                ?: false
                    }
                }

        if (lastRowWithNextMessage == -1) {
            for (layer in dataflowDag.layers) {
                for (vertexName in layer) {
                    val vertexModel = activeDataflowModel.vertices[vertexName]!!
                    if (vertexModel.message != null) {
                        vertexModel.message = null
                        loopConsumer.invoke(vertexName)
                    }
                }
            }
        }
        else {
            for (followingVertex in dataflowDag.layers[lastRowWithNextMessage]) {
                val vertexModel = activeDataflowModel.vertices[followingVertex]!!

                if (vertexModel.message != null) {
                    vertexModel.message = null
                    loopConsumer.invoke(followingVertex)
                }
            }

            val followingLayers = dataflowDag.layers.subList(
                    lastRowWithNextMessage + 1, dataflowDag.layers.size)
            for (followingLayer in followingLayers) {
                for (followingVertex in followingLayer) {
                    val vertexModel = activeDataflowModel.vertices[followingVertex]!!

                    if (vertexModel.epoch > 0) {
                        vertexModel.epoch = 0
                        vertexModel.message = null
                        clearedConsumer.invoke(followingVertex)
                    }
                }
            }
        }
    }


    private suspend fun executeDirect(
            activeDataflowModel: ActiveDataflowModel,
            vertexLocation: ObjectLocation,
            dataflowDag: DataflowDag
    ) {
        val activeVertexModel = activeDataflowModel.vertices[vertexLocation]!!

        if (activeVertexModel.remainingBatch.isNotEmpty()) {
            val nextMessage = activeVertexModel.remainingBatch.removeAt(0)
            activeVertexModel.message = nextMessage
        }
        else {
            val instance = instanceManager.get(vertexLocation)

            @Suppress("UNCHECKED_CAST")
            val dataflow = instance.reference as Dataflow<Any?>

            val input = instance.constructorAttributes[DataflowUtils.inputAttributeName] as? MutableRequiredInput<*>
            if (input != null) {
                val predecessors = dataflowDag.predecessors[vertexLocation]
                val inputLocation: ObjectLocation = predecessors?.first()
                        ?: throw IllegalArgumentException("Unknown vertexLocation $vertexLocation: $predecessors")

                val inputActiveModel = activeDataflowModel.vertices[inputLocation]!!

                val message = inputActiveModel.message
                input.set(message)
            }

            val nextState =
                    when {
                        activeVertexModel.streamHasNext ->
                            (dataflow as StreamDataflow).next(activeVertexModel.state!!)

                        activeVertexModel.state == null -> {
                            dataflow.process(Unit)
                            null
                        }

                        else ->
                            dataflow.process(activeVertexModel.state)
                    }

            val output = instance.constructorAttributes[DataflowUtils.outputAttributeName] as? MutableDataflowOutput<*>
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
        }

        activeVertexModel.epoch++
    }
}