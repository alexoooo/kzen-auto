package tech.kzen.auto.server.api.handler.command

import io.ktor.http.*
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.server.api.handler.getParam
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.cqrs.*


//---------------------------------------------------------------------------------------------------------------------
fun NotationCommandHandler.createDocument(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    // the path form (trailing slash) distinguishes a pure folder from a document — route accordingly
    if (documentPath.folder) {
        return applyCommand(CreateFolderCommand(documentPath)).asString()
    }

    val documentBody = parameters.getParam(CommonRestApi.paramDocumentNotation) {
        yamlNotationParser.parseDocumentObjects(it)
    }

    val command = CreateDocumentCommand(documentPath, documentBody)
    return applyCommand(command).asString()
}


fun NotationCommandHandler.deleteDocument(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val command =
        if (documentPath.folder) {
            DeleteFolderCommand(documentPath)
        }
        else {
            DeleteDocumentCommand(documentPath)
        }
    return applyCommand(command).asString()
}


fun NotationCommandHandler.setDocumentObjects(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val documentObjectNotation = parameters.getParam(
        CommonRestApi.paramRawObjectsYaml, yamlNotationParser::parseDocumentObjects)

    val command = SetDocumentObjectsCommand(documentPath, documentObjectNotation)
    return applyCommand(command).asString()
}
