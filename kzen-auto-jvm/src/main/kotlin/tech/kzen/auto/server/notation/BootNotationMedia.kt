package tech.kzen.auto.server.notation

import com.google.common.base.MoreObjects
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.structure.scan.DocumentScan
import tech.kzen.lib.common.model.structure.scan.NotationScan
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.toPersistentMap


class BootNotationMedia(
        private val prefix: String = NotationConventions.documentPathPrefix,
        private val suffix: String = NotationConventions.fileDocumentSuffix,
        private val loader: ClassLoader = Thread.currentThread().contextClassLoader
): NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        val gradleResourcesInfix = "/out/production/resources/"
//    }


    //-----------------------------------------------------------------------------------------------------------------
    private val cache: MutableMap<DocumentPath, DocumentScan> = mutableMapOf()


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun scan(): NotationScan {
        if (cache.isEmpty()) {
            val paths = scanPaths()

            for (path in paths) {
                check(! path.directory)

                val bytes = readDocument(path)
                val digest = Digest.ofUtf8(bytes)
                cache[path] = DocumentScan(
                        digest,
                        null)
            }
        }
        return NotationScan(DocumentPathMap(cache.toPersistentMap()))
    }


    private fun scanPaths(): List<DocumentPath> {
        val patternResolver = PathMatchingResourcePatternResolver(loader)

        val pattern = "classpath*:$prefix**/*$suffix"
        val classResources = patternResolver.getResources(pattern)

        val builder = mutableListOf<DocumentPath>()

        for (resource in classResources) {
            val fullPath = try {
                val uri = resource.uri
                MoreObjects.firstNonNull(
                        uri.path, uri.schemeSpecificPart)
            } catch (e: Exception) {
                val url = resource.url
                url.path
            }

//            var start = fullPath.lastIndexOf('!')
//            if (start == -1) {
//                val fileStart = fullPath.indexOf(gradleResourcesInfix)
//                checkState(fileStart != -1, "Unknown path: %s", resource)
//                start = fileStart + gradleResourcesInfix.length
//            }
//            else {
//                start += 1
//            }

            val start = fullPath.lastIndexOf('!')
            if (start == -1) {
                continue
            }

            // strip jar '!' and leading '/'
            val innerPath = fullPath.substring(start + 2)

            if (! DocumentPath.matches(innerPath)) {
                continue
            }

            val afterPrefix = innerPath.substring(prefix.length)

            builder.add(DocumentPath.parse(afterPrefix))
        }

        return builder
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readDocument(documentPath: DocumentPath): String {
        val bytes = loader.getResource(prefix + documentPath.asRelativeFile())!!.readText()
        println("ClasspathNotationMedia - read ${bytes.length}")
        return bytes
    }


    override suspend fun writeDocument(documentPath: DocumentPath, contents: String) {
        throw UnsupportedOperationException("Classpath resource writing not supported")
    }


    override suspend fun copyResource(resourceLocation: ResourceLocation, destination: ResourceLocation) {
        throw UnsupportedOperationException("Classpath resource copying not supported")
    }


    override suspend fun deleteDocument(documentPath: DocumentPath) {
        throw UnsupportedOperationException("Classpath resource deleting not supported")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun deleteResource(resourceLocation: ResourceLocation) {
        TODO("not implemented")
    }

    override suspend fun readResource(resourceLocation: ResourceLocation): ByteArray {
        throw UnsupportedOperationException("Classpath writing not supported")
    }

    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ByteArray) {
        throw UnsupportedOperationException("Classpath deleting not supported")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun invalidate() {
        cache.clear()
    }
}