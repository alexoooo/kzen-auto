package tech.kzen.auto.client.service.rest

import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.service.store.RemoteGraphStore
import tech.kzen.lib.common.util.Digest


class ClientRestGraphStore(
        private val restClient: ClientRestApi,
        private val notationParser: NotationParser
): RemoteGraphStore {
    override suspend fun apply(command: NotationCommand): Digest {
        return when (command) {
            is CreateDocumentCommand -> {
                val unparsed = notationParser.unparseDocument(command.documentObjectNotation, "")
                restClient.createDocument(
                        command.documentPath, unparsed)
            }


            is DeleteDocumentCommand ->
                restClient.deleteDocument(
                        command.documentPath)


            is AddObjectCommand -> {
                val unparsed = notationParser.unparseObject(command.body)
                restClient.addObject(
                        command.objectLocation, command.indexInDocument, unparsed)
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
                val unparsed = notationParser.unparseObject(command.body)
                restClient.insertObjectInList(
                        command.containingObjectLocation,
                        command.containingList,
                        command.indexInList,
                        command.objectName,
                        command.positionInDocument,
                        unparsed)
            }

            is RemoveObjectInAttributeCommand -> {
                restClient.removeObjectInAttribute(
                        command.containingObjectLocation,
                        command.attributePath)
            }


            is UpsertAttributeCommand -> {
                val unparsed = notationParser.unparseAttribute(command.attributeNotation)
                restClient.upsertAttribute(
                        command.objectLocation, command.attributeName, unparsed)
            }


            is UpdateInAttributeCommand -> {
                val unparsed = notationParser.unparseAttribute(command.attributeNotation)
                restClient.updateInAttribute(
                        command.objectLocation, command.attributePath, unparsed)
            }


            is InsertListItemInAttributeCommand -> {
                val unparsed = notationParser.unparseAttribute(command.item)
                restClient.insertListItemInAttribute(
                        command.objectLocation, command.containingList, command.indexInList, unparsed)
            }


            is InsertMapEntryInAttributeCommand -> {
                val unparsed = notationParser.unparseAttribute(command.value)
                restClient.insertMapEntryInAttribute(
                        command.objectLocation, command.containingMap, command.indexInMap, command.mapKey, unparsed)
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


            is AddResourceCommand ->
                restClient.addResource(
                        command.resourceLocation, command.resourceContent.value)


            is RemoveResourceCommand ->
                restClient.removeResource(
                        command.resourceLocation)


            else ->
                throw UnsupportedOperationException("Unknown: $command")
        }
    }
}