package tech.kzen.auto.client.service

import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.common.service.GraphStructureManager
import tech.kzen.lib.common.structure.notation.edit.*
import tech.kzen.lib.common.structure.notation.io.NotationParser
import tech.kzen.lib.common.structure.notation.repo.NotationRepository
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.IoUtils


// TODO: move to lib?
class CommandBus(
        private val clientRepository: NotationRepository,
        private val graphStructureManager: GraphStructureManager,
        private val restClient: ClientRestApi,
        private val notationParser: NotationParser
) {
    //-----------------------------------------------------------------------------------------------------------------
    interface Subscriber {
        fun onCommandFailedInClient(command: NotationCommand, cause: Throwable)
        fun onCommandSuccess(command: NotationCommand, event: NotationEvent)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val subscribers = mutableSetOf<Subscriber>()


    fun subscribe(observer: Subscriber) {
        subscribers.add(observer)
    }

    fun unsubscribe(observer: Subscriber) {
        subscribers.remove(observer)
    }


    private fun onCommandFailedInClient(command: NotationCommand, cause: Throwable) {
        for (observer in subscribers) {
            observer.onCommandFailedInClient(command, cause)
        }
    }

    private fun onCommandSuccess(command: NotationCommand, event: NotationEvent) {
        for (observer in subscribers) {
            observer.onCommandSuccess(command, event)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun apply(command: NotationCommand) {
//        console.log("CommandBus - applying command: $command", command)

        // NB: for now, this has to happen before clientEvent for VisualDataflowProvider.inspectVertex
        // TODO: make this parallel with client processing via VisualDataflowProvider.initialVertexState
        val restDigest = applyRest(command)

        val clientEvent = try {
            clientRepository.apply(command)
        }
        catch (e: Throwable) {
            console.log("CommandBus - caught error in client: $command", e)
            onCommandFailedInClient(command, e)
            return
        }
//        console.log("CommandBus - client event:",
//                event, clientRepository.aggregate().state.coalesce.values.keys.toList())

//        val bundleValue = ClientContext.notationMediaCache.read(NotationConventions.mainPath)
//        println("CommandBus - new bundle value !!: ${IoUtils.utf8Decode(bundleValue)}")

//        val parsedBundle = ClientContext.notationParser.parseBundle(bundleValue)
//        println("CommandBus - parsedBundle: $parsedBundle")

        val clientDigest = clientRepository.digest()
//        println("CommandBus - applied in client: $clientDigest")

        graphStructureManager.onEvent(clientEvent)

//        println("CommandBus - applied in REST: $restDigest")

        if (clientDigest != restDigest) {
            graphStructureManager.refresh()
        }

        onCommandSuccess(command, clientEvent)
    }


    private suspend fun applyRest(command: NotationCommand): Digest =
        when (command) {
            is CreateDocumentCommand -> {
                val deparsed = notationParser.deparseDocument(command.documentNotation, ByteArray(0))
                restClient.createDocument(
                        command.documentPath, IoUtils.utf8Decode(deparsed))
            }


            is DeleteDocumentCommand ->
                restClient.deleteDocument(
                        command.documentPath)


            is AddObjectCommand -> {
                val deparsed = notationParser.deparseObject(command.body)
                restClient.addObject(
                        command.objectLocation, command.indexInDocument, deparsed)
            }


            is RemoveObjectCommand ->
                restClient.removeObject(
                        command.objectLocation)


            is ShiftObjectCommand ->
                restClient.shiftObject(
                        command.objectLocation, command.newPositionInDocument)


            is RenameObjectCommand ->
                restClient.renameObject(
                        command.objectLocation, command.newName)


            is InsertObjectInListAttributeCommand -> {
                val deparsed = notationParser.deparseObject(command.body)
                restClient.insertObjectInList(
                        command.containingObjectLocation,
                        command.containingList,
                        command.indexInList,
                        command.objectName,
                        command.positionInDocument,
                        deparsed)
            }

            is RemoveObjectInAttributeCommand -> {
                restClient.removeObjectInAttribute(
                        command.containingObjectLocation,
                        command.attributePath)
            }


            is UpsertAttributeCommand -> {
                val deparsed = notationParser.deparseAttribute(command.attributeNotation)
                restClient.upsertAttribute(
                        command.objectLocation, command.attributeName, deparsed)
            }


            is UpdateInAttributeCommand -> {
                val deparsed = notationParser.deparseAttribute(command.attributeNotation)
                restClient.updateInAttribute(
                        command.objectLocation, command.attributePath, deparsed)
            }


            is InsertListItemInAttributeCommand -> {
                val deparsed = notationParser.deparseAttribute(command.item)
                restClient.insertListItemInAttribute(
                        command.objectLocation, command.containingList, command.indexInList, deparsed)
            }


            is InsertMapEntryInAttributeCommand -> {
                val deparsed = notationParser.deparseAttribute(command.value)
                restClient.insertMapEntryInAttribute(
                        command.objectLocation, command.containingMap, command.indexInMap, command.mapKey, deparsed)
            }


            is RemoveInAttributeCommand ->
                restClient.removeInAttribute(
                        command.objectLocation, command.attributePath)


            is ShiftInAttributeCommand ->
                restClient.shiftInAttribute(
                        command.objectLocation, command.attributePath, command.newPosition)

            is RenameObjectRefactorCommand ->
                restClient.refactorName(
                        command.objectLocation, command.newName)


            is RenameDocumentRefactorCommand ->
                restClient.refactorDocumentName(
                        command.documentPath, command.newName)


            else ->
                throw UnsupportedOperationException("Unknown: $command")
        }
}