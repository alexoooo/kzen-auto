package tech.kzen.auto.server.api.handler.command

import io.ktor.http.*
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.server.api.handler.getParam
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.util.ImmutableByteArray


//---------------------------------------------------------------------------------------------------------------------
fun NotationCommandHandler.addResource(parameters: Parameters, body: ImmutableByteArray): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val resourcePath: ResourcePath = parameters.getParam(
        CommonRestApi.paramResourcePath, ResourcePath::parse)

    val command = AddResourceCommand(
        ResourceLocation(documentPath, resourcePath),
        body)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.resourceDelete(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val resourcePath: ResourcePath = parameters.getParam(
        CommonRestApi.paramResourcePath, ResourcePath::parse)

    val command = RemoveResourceCommand(
        ResourceLocation(documentPath, resourcePath))

    return applyCommand(command).asString()
}
