package tech.kzen.auto.server.api.handler.command

import io.ktor.http.*
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.server.api.handler.getObjectLocationParam
import tech.kzen.auto.server.api.handler.getParam
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.cqrs.*


//---------------------------------------------------------------------------------------------------------------------
fun NotationCommandHandler.refactorObjectName(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val newName: ObjectName = parameters.getParam(
        CommonRestApi.paramObjectName, ::ObjectName)

    val command = RenameObjectRefactorCommand(
        objectLocation,
        newName)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.refactorDocumentName(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val newName: DocumentName = parameters.getParam(
        CommonRestApi.paramDocumentName, ::DocumentName)

    // the path form (trailing slash) distinguishes a pure folder from a document — route accordingly
    val command =
        if (documentPath.folder) {
            RenameFolderRefactorCommand(documentPath, newName)
        }
        else {
            RenameDocumentRefactorCommand(documentPath, newName)
        }

    return applyCommand(command).asString()
}


fun NotationCommandHandler.refactorMove(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val newNesting: DocumentNesting = parameters.getParam(
        CommonRestApi.paramDocumentNesting, DocumentNesting::parse)

    // the path form (trailing slash) distinguishes a pure folder from a document — route accordingly
    val command =
        if (documentPath.folder) {
            MoveFolderRefactorCommand(documentPath, newNesting)
        }
        else {
            MoveDocumentRefactorCommand(documentPath, newNesting)
        }

    return applyCommand(command).asString()
}
