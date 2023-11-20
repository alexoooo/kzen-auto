package tech.kzen.auto.common.paradigm.dataflow.service.active

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.dataflow.api.Dataflow
import tech.kzen.auto.common.paradigm.dataflow.api.StreamDataflow
import tech.kzen.auto.common.paradigm.dataflow.model.channel.MutableDataflowOutput
import tech.kzen.auto.common.paradigm.dataflow.model.channel.MutableInput
import tech.kzen.auto.common.paradigm.dataflow.model.exec.*
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowDag
import tech.kzen.auto.common.paradigm.dataflow.model.structure.DataflowMatrix
import tech.kzen.auto.common.paradigm.dataflow.service.format.DataflowMessageInspector
import tech.kzen.auto.common.paradigm.dataflow.util.DataflowUtils
import tech.kzen.auto.common.service.GraphInstanceCreator
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.instance.ObjectInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.platform.collect.toPersistentMap


class ActiveDataflowRepository(
        private val instanceCreator: GraphInstanceCreator,
        private val dataflowMessageInspector: DataflowMessageInspector,
        private val graphStore: LocalGraphStore
) :
        LocalGraphStore.Observer
{
//    //-----------------------------------------------------------------------------------------------------------------
//    private class Handle(
//            val input: ObjectLocation?,
//            val output: ObjectLocation?
//    )


    //-----------------------------------------------------------------------------------------------------------------
    private var models: MutableMap<DocumentPath, ActiveDataflowModel> = mutableMapOf()


    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        // NB: avoid concurrent modification for DeletedDocumentEvent handling
        val modelHosts = models.keys.toList()

        for (host in modelHosts) {
            apply(host, event)
        }
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}

    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {}


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
        if (documentPath != event.documentPath) {
            return
        }

        val model = models[documentPath]
                ?: return

        when (event) {
            is RemovedObjectEvent -> {
                model.vertices.remove(event.objectLocation)
            }

            is RenamedObjectEvent -> {
                val activeVertexModel = model.vertices.remove(event.objectLocation)!!
                val newObjectPath = event.objectLocation.objectPath.copy(name = event.newName)
                val newLocation = ObjectLocation(documentPath, newObjectPath)
                model.vertices[newLocation] = activeVertexModel
            }

            is AddedObjectEvent -> {
                val initialVertexModel = initializeVertex(event.objectLocation)
                model.vertices[event.objectLocation] = initialVertexModel
            }

            is DeletedDocumentEvent -> {
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
        if (documentPath != event.documentPath) {
            return false
        }

        val model = models[documentPath]!!

        return when (event) {
            is RenamedDocumentRefactorEvent -> {
                model.move(
                        event.removedUnderOldName.documentPath, event.createdWithNewName.destination)

                models.remove(event.removedUnderOldName.documentPath)
                models[event.createdWithNewName.destination] = model

                true
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

        val serverGraphStructure = graphStore
                .graphStructure()
                .filter(AutoConventions.serverAllowed)

        check(host in serverGraphStructure.graphNotation.documents)

        val vertexMatrix = DataflowMatrix.ofGraphDocument(host, serverGraphStructure)

        val builder = mutableMapOf<ObjectLocation, ActiveVertexModel>()
        for (vertexLocation in vertexMatrix.verticesByLocation.keys) {
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
        val vertexInstance = instanceCreator.create(vertexLocation).reference as Dataflow<*>

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
        val dataflow = instanceCreator.create(vertexLocation).reference as Dataflow<Any>
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
        val serverGraphStructure = graphStore
                .graphStructure()
                .filter(AutoConventions.serverAllowed)

        val dataflowMatrix = DataflowMatrix.ofGraphDocument(host, serverGraphStructure)
        val dataflowDag = DataflowDag.of(dataflowMatrix)

        val activeDataflowModel = getOrInit(host)

        executeDirect(
                activeDataflowModel,
                vertexLocation,
                dataflowMatrix)

        val visualDataflowModel = inspect(host)

        if (isDone(dataflowMatrix, dataflowDag, visualDataflowModel)) {
            clearIteration(dataflowDag, activeDataflowModel, loopConsumer, clearedConsumer)
        }
    }


    private fun isDone(
            dataflowMatrix: DataflowMatrix,
            dataflowDag: DataflowDag,
            visualDataflowModel: VisualDataflowModel
    ): Boolean {
        val next = DataflowUtils.next(dataflowMatrix, dataflowDag, visualDataflowModel)
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
            dataflowMatrix: DataflowMatrix
    ) {
        val activeVertexModel = activeDataflowModel.vertices[vertexLocation]!!

        if (activeVertexModel.remainingBatch.isNotEmpty()) {
            val nextMessage = activeVertexModel.remainingBatch.removeAt(0)
            activeVertexModel.message = nextMessage
        }
        else {
            val instance = instanceCreator.create(vertexLocation)

            @Suppress("UNCHECKED_CAST")
            val dataflow = instance.reference as Dataflow<Any?>

            populateInputs(
                    instance,
                    activeDataflowModel,
                    vertexLocation,
                    dataflowMatrix)

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

            val output = instance
                    .constructorAttributes[DataflowUtils.mainOutputAttributeName] as? MutableDataflowOutput<*>
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


    private fun populateInputs(
            dataflowInstance: ObjectInstance,
            activeDataflowModel: ActiveDataflowModel,
            vertexLocation: ObjectLocation,
            dataflowMatrix: DataflowMatrix
    ) {
        val vertexDescriptor = dataflowMatrix.verticesByLocation[vertexLocation]
                ?: throw IllegalStateException("Vertex not found in matrix: $vertexLocation")

        val inputAttributes = vertexDescriptor.inputNames
        if (inputAttributes.isEmpty()) {
            return
        }

        var populatedInputCount = 0
        for (inputAttribute in inputAttributes) {
            val sourceVertex = dataflowMatrix.traceVertexBackFrom(vertexDescriptor, inputAttribute)
                    ?: continue

            @Suppress("UNCHECKED_CAST")
            val input = dataflowInstance.constructorAttributes[inputAttribute] as? MutableInput<Any>
                    ?: throw IllegalArgumentException("Unknown vertexLocation $vertexLocation: $sourceVertex")

            val inputLocation = sourceVertex.objectLocation
            val inputActiveModel = activeDataflowModel.vertices[inputLocation]!!

            val message = inputActiveModel.message
            if (message != null) {
                input.set(message)
            }
            else {
                input.clear()
            }

            populatedInputCount++
        }

        // TODO: enforce optional/required contracts
        check(populatedInputCount > 0) {
            "Vertex must receive at least one input"
        }
    }
}