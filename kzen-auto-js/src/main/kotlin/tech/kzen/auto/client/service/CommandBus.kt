package tech.kzen.auto.client.service

//import tech.kzen.auto.client.service.rest.ClientRestApi
//import tech.kzen.auto.common.service.GraphStructureManager
//import tech.kzen.lib.common.model.structure.notation.cqrs.*
//import tech.kzen.lib.common.service.context.NotationRepository
//import tech.kzen.lib.common.service.parse.NotationParser
//import tech.kzen.lib.common.util.Digest
//
//
//// TODO: move to lib?
//class CommandBus(
//        private val clientRepository: NotationRepository,
//        private val graphStructureManager: GraphStructureManager,
//        private val restClient: ClientRestApi,
//        private val notationParser: NotationParser
//) {
//    //-----------------------------------------------------------------------------------------------------------------
//    interface Subscriber {
//        fun onCommandFailedInClient(command: NotationCommand, cause: Throwable)
//        fun onCommandSuccess(command: NotationCommand, event: NotationEvent)
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private val subscribers = mutableSetOf<Subscriber>()
//
//
//    fun subscribe(observer: Subscriber) {
//        subscribers.add(observer)
//    }
//
//    fun unsubscribe(observer: Subscriber) {
//        subscribers.remove(observer)
//    }
//
//
//    private fun onCommandFailedInClient(command: NotationCommand, cause: Throwable) {
//        for (observer in subscribers) {
//            observer.onCommandFailedInClient(command, cause)
//        }
//    }
//
//    private fun onCommandSuccess(command: NotationCommand, event: NotationEvent) {
//        for (observer in subscribers) {
//            observer.onCommandSuccess(command, event)
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    suspend fun apply(command: NotationCommand) {
//        console.log("CommandBus - applying command: $command", command)
//
//        // NB: for now, this has to happen before clientEvent for VisualDataflowProvider.inspectVertex
//        // TODO: make this parallel with client processing via VisualDataflowProvider.initialVertexState
//        val restDigest = applyRest(command)
//
//        val clientEvent = try {
//            clientRepository.apply(command)
//        }
//        catch (e: Throwable) {
//            console.log("CommandBus - caught error in client: $command", e)
//            onCommandFailedInClient(command, e)
//            return
//        }
////        console.log("CommandBus - client event:",
////                event, clientRepository.aggregate().state.coalesce.values.keys.toList())
//
////        val bundleValue = ClientContext.notationMediaCache.read(NotationConventions.mainPath)
////        println("CommandBus - new bundle value !!: ${IoUtils.utf8Decode(bundleValue)}")
//
////        val parsedBundle = ClientContext.notationParser.parseBundle(bundleValue)
////        println("CommandBus - parsedBundle: $parsedBundle")
//
//        val clientDigest = clientRepository.digest()
////        println("CommandBus - applied in client: $clientDigest")
//
//        graphStructureManager.onEvent(clientEvent)
//
////        println("CommandBus - applied in REST: $restDigest")
//
//        if (clientDigest != restDigest) {
//            graphStructureManager.refresh()
//        }
//
//        onCommandSuccess(command, clientEvent)
//    }
//
//
//    private suspend fun applyRest(command: NotationCommand): Digest =
//        when (command) {
//            is CreateDocumentCommand -> {
//                val unparsed = notationParser.unparseDocument(command.documentNotation, "")
//                restClient.createDocument(
//                        command.documentPath, unparsed)
//            }
//
//
//            is DeleteDocumentCommand ->
//                restClient.deleteDocument(
//                        command.documentPath)
//
//
//            is AddObjectCommand -> {
//                val unparsed = notationParser.unparseObject(command.body)
//                restClient.addObject(
//                        command.objectLocation, command.indexInDocument, unparsed)
//            }
//
//
//            is RemoveObjectCommand ->
//                restClient.removeObject(
//                        command.objectLocation)
//
//
//            is ShiftObjectCommand ->
//                restClient.shiftObject(
//                        command.objectLocation, command.newPositionInDocument)
//
//
//            is RenameObjectCommand ->
//                restClient.renameObject(
//                        command.objectLocation, command.newName)
//
//
//            is InsertObjectInListAttributeCommand -> {
//                val unparsed = notationParser.unparseObject(command.body)
//                restClient.insertObjectInList(
//                        command.containingObjectLocation,
//                        command.containingList,
//                        command.indexInList,
//                        command.objectName,
//                        command.positionInDocument,
//                        unparsed)
//            }
//
//            is RemoveObjectInAttributeCommand -> {
//                restClient.removeObjectInAttribute(
//                        command.containingObjectLocation,
//                        command.attributePath)
//            }
//
//
//            is UpsertAttributeCommand -> {
//                val unparsed = notationParser.unparseAttribute(command.attributeNotation)
//                restClient.upsertAttribute(
//                        command.objectLocation, command.attributeName, unparsed)
//            }
//
//
//            is UpdateInAttributeCommand -> {
//                val unparsed = notationParser.unparseAttribute(command.attributeNotation)
//                restClient.updateInAttribute(
//                        command.objectLocation, command.attributePath, unparsed)
//            }
//
//
//            is InsertListItemInAttributeCommand -> {
//                val unparsed = notationParser.unparseAttribute(command.item)
//                restClient.insertListItemInAttribute(
//                        command.objectLocation, command.containingList, command.indexInList, unparsed)
//            }
//
//
//            is InsertMapEntryInAttributeCommand -> {
//                val unparsed = notationParser.unparseAttribute(command.value)
//                restClient.insertMapEntryInAttribute(
//                        command.objectLocation, command.containingMap, command.indexInMap, command.mapKey, unparsed)
//            }
//
//
//            is RemoveInAttributeCommand ->
//                restClient.removeInAttribute(
//                        command.objectLocation, command.attributePath)
//
//
//            is ShiftInAttributeCommand ->
//                restClient.shiftInAttribute(
//                        command.objectLocation, command.attributePath, command.newPosition)
//
//            is RenameObjectRefactorCommand ->
//                restClient.refactorName(
//                        command.objectLocation, command.newName)
//
//
//            is RenameDocumentRefactorCommand ->
//                restClient.refactorDocumentName(
//                        command.documentPath, command.newName)
//
//
//            else ->
//                throw UnsupportedOperationException("Unknown: $command")
//        }
//}