package tech.kzen.auto.server.api

import kotlinx.coroutines.experimental.runBlocking
import com.google.common.io.MoreFiles
import com.google.common.io.Resources
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.read.flat.source.FallbackNotationSource
import tech.kzen.lib.common.notation.scan.LiteralNotationScanner
import tech.kzen.lib.common.notation.scan.NotationScanner
import tech.kzen.lib.common.util.IoUtils
import tech.kzen.lib.server.notation.ClasspathNotationSource
import tech.kzen.lib.server.notation.DirectoryNotationScanner
import tech.kzen.lib.server.notation.FileNotationSource
import tech.kzen.lib.server.notation.GradleNotationSource
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


@Component
class RestHandler {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val classPathRoots = listOf(
                URI("classpath:/public/"))

        val resourceDirectories = listOf<Path>(
                // IntelliJ and typical commandline working dir is project root
                Paths.get("server/src/main/resources/public/"),
                Paths.get("client/build/dist/"),

                // Eclipse default active working directory is the module
                Paths.get("src/main/resources/public/"),
                Paths.get("../client/build/dist/"))

        val allowedExtensions = listOf(
                "html",
                "js")
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun scan(serverRequest: ServerRequest): Mono<ServerResponse> {
        val notationScanner: NotationScanner = LiteralNotationScanner(listOf(
                "notation/base/kzen-base.yaml",
                "notation/auto/kzen-auto.yaml"))

        val projectPaths = runBlocking {
            notationScanner.scan()
        }
//        call.respondText(gson.toJson(projectPaths), ContentType.Application.Json)

        return ServerResponse
                .ok()
//                .body(Mono.just("Foo: ..."))
                .body(Mono.just(projectPaths))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun notation(serverRequest: ServerRequest): Mono<ServerResponse> {
        val notationSource = FallbackNotationSource(listOf(
                GradleNotationSource(FileNotationSource()),
                ClasspathNotationSource()))

        val notationPrefix = "/notation/"
        val requestSuffix = serverRequest.path().substring(notationPrefix.length)

        val notationPath = ProjectPath(requestSuffix)
        val notationBytes = runBlocking {
            notationSource.read(notationPath)
        }

        val notationText = IoUtils.utf8ToString(notationBytes)

        return ServerResponse
                .ok()
                .body(Mono.just(notationText))
    }


    //-----------------------------------------------------------------------------------------------------------------
    // TODO: is this secure?
    fun resource(serverRequest: ServerRequest): Mono<ServerResponse> {
        val excludingInitialSlash = serverRequest.path().substring(1)

        val resolvedPath =
                if (excludingInitialSlash == "") {
                    "index.html"
                }
                else {
                    excludingInitialSlash
                }

        val path = Paths.get(resolvedPath).normalize()

        if (! isResourceAllowed(path)) {
            return ServerResponse
                    .badRequest()
                    .build()
        }

        val bytes: ByteArray = readResource(path)
                ?: return ServerResponse
                        .notFound()
                        .build()

        return ServerResponse
                .ok()
                .body(Mono.just(bytes))
    }


    private fun isResourceAllowed(path: Path): Boolean {
        if (path.isAbsolute) {
            return false
        }

        val extension = MoreFiles.getFileExtension(path)
        return allowedExtensions.contains(extension)
    }


    private fun readResource(relativePath: Path): ByteArray? {
        for (root in classPathRoots) {
            try {
                val resourceLocation: URI = root.resolve(relativePath.toString())
                val resourceUrl = Resources.getResource(resourceLocation.path)
                return Resources.toByteArray(resourceUrl)
            }
            catch (ignored: Exception) {}
        }

        for (root in resourceDirectories) {
            val candidate = root.resolve(relativePath)
            if (Files.exists(candidate)) {
                return Files.readAllBytes(candidate)
            }
        }

        return null
    }
}