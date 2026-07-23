package tech.kzen.auto.server.api.handler

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.util.scan.NotationScanDocument
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.service.media.NotationMedia
import java.net.URI


class NotationQueryHandler(
    private val notationMedia: NotationMedia
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun scan(parameters: Parameters): Map<String, NotationScanDocument> {
        val fresh = parameters[CommonRestApi.paramFresh] == "true"
        return scan(fresh)
    }


    fun scan(fresh: Boolean): Map<String, NotationScanDocument> {
        if (fresh) {
            notationMedia.invalidate()
        }

        val documentTree = runBlocking {
            notationMedia.scan()
        }

        val asMap = mutableMapOf<String, NotationScanDocument>()

        for (e in documentTree.documents.map) {
            asMap[e.key.asRelativeFile()] = NotationScanDocument(
                documentDigest = e.value.documentDigest.asString(),
                resources = e.value.resources?.digests?.map {
                    it.key.asString() to it.value.asString()
                }?.toMap()
            )
        }

        return asMap
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun resourceRead(parameters: Parameters): ByteArray {
        val documentPath: DocumentPath = parameters.getParam(
            CommonRestApi.paramDocumentPath, DocumentPath::parse)

        val resourcePath: ResourcePath = parameters.getParam(
            CommonRestApi.paramResourcePath, ResourcePath::parse)

        val resourceLocation = ResourceLocation(documentPath, resourcePath)

        val resourceContents = runBlocking {
            notationMedia.readResource(resourceLocation)
        }

        return resourceContents.toByteArray()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun notation(notationPath: String, notationPathUrlEncoded: Boolean): String {
        val decodedNotationPath =
            if (notationPathUrlEncoded) {
                URI(notationPath).path
            }
            else {
                notationPath
            }

        val parsedNotationPath = DocumentPath.parse(decodedNotationPath)
        val notationText = runBlocking {
            notationMedia.readDocument(parsedNotationPath)
        }
        return notationText
    }


    fun notationBatch(parameters: Parameters): Map<String, String> {
        val rawPaths = parameters.getAll(CommonRestApi.paramDocumentPath)
            ?: return emptyMap()

        val paths = rawPaths.map { DocumentPath.parse(it) }

        val documents = runBlocking {
            notationMedia.readDocuments(paths)
        }

        return paths.associate { path ->
            path.asRelativeFile() to (documents[path]
                ?: throw IllegalStateException("Missing document: $path"))
        }
    }
}
