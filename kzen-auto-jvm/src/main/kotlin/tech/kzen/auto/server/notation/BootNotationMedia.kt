package tech.kzen.auto.server.notation

import com.google.common.base.MoreObjects
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.BundleTree
import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.util.Digest


class BootNotationMedia(
        private val prefix: String = NotationConventions.prefix,
        private val suffix: String = NotationConventions.suffix,
        private val loader: ClassLoader = Thread.currentThread().contextClassLoader
) : NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val gradleResourcesInfix = "/out/production/resources/"
    }

    //-----------------------------------------------------------------------------------------------------------------
    private val cache: MutableMap<BundlePath, Digest> = mutableMapOf()


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun scan(): BundleTree<Digest> {
        if (cache.isEmpty()) {
            val paths = scanPaths()

            for (path in paths) {
                val bytes = read(path)
                val digest = Digest.ofXoShiRo256StarStar(bytes)
                cache[path] = digest
            }
        }
        return BundleTree(cache)
    }


    private fun scanPaths(): List<BundlePath> {
        val patternResolver = PathMatchingResourcePatternResolver(loader)

        val pattern = "classpath*:$prefix**/*$suffix"
        val classResources = patternResolver.getResources(pattern)

        val builder = mutableListOf<BundlePath>()

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

            if (! BundlePath.matches(innerPath)) {
                continue
            }

            val afterPrefix = innerPath.substring(prefix.length)

            builder.add(BundlePath.parse(afterPrefix))
        }

        return builder
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun read(location: BundlePath): ByteArray {
        val bytes = loader.getResource(prefix + location.asRelativeFile()).readBytes()
        println("ClasspathNotationMedia - read ${bytes.size}")
        return bytes
    }


    override suspend fun write(location: BundlePath, bytes: ByteArray) {
        throw UnsupportedOperationException("Classpath writing not supported")
    }

    override suspend fun delete(location: BundlePath) {
        throw UnsupportedOperationException("Classpath deleting not supported")
    }
}