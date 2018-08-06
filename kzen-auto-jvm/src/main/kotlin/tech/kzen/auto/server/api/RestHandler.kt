package tech.kzen.auto.server.api

import kotlinx.coroutines.experimental.runBlocking
import com.google.common.io.MoreFiles
import com.google.common.io.Resources
import com.google.common.primitives.Ints
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import tech.kzen.lib.common.edit.*
import tech.kzen.lib.common.notation.format.YamlNotationParser
import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.notation.io.common.MultiNotationMedia
import tech.kzen.lib.common.notation.model.*
import tech.kzen.lib.common.notation.repo.NotationRepository
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.IoUtils
import tech.kzen.lib.server.notation.*
import tech.kzen.lib.server.notation.locate.GradleLocator
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*


@Component
class RestHandler {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val classPathRoots = listOf(
                URI("classpath:/public/"))

        val resourceDirectories = listOf<Path>(
                // TODO: dynamically discover these
                // IntelliJ and typical commandline working dir is project root
                Paths.get("kzen-auto-jvm/src/main/resources/public/"),
                Paths.get("kzen-auto-js/build/dist/"),

                // Eclipse and Gradle default active working directory is the module
                Paths.get("src/main/resources/public/"),
                Paths.get("../kzen-auto-js/build/dist/"))

        val allowedExtensions = listOf(
                "html",
                "js",
                "css")
    }


    private val fileLocator = GradleLocator()
    private val fileMedia = FileNotationMedia(fileLocator)

    private val classpathMedia = ClasspathNotationMedia()

    private val notationMedia: NotationMedia = MultiNotationMedia(listOf(
            fileMedia, classpathMedia))

//    private val notationScanner: NotationScanner = MultiNotationScanner(listOf(
//        ClasspathNotationScanner(media = classpathMedia),
//        DirectoryNotationScanner(fileLocator, fileMedia)))

    private val yamlParser = YamlNotationParser()

    private val repository = NotationRepository(
            notationMedia,
            yamlParser)


    //-----------------------------------------------------------------------------------------------------------------
    fun scan(serverRequest: ServerRequest): Mono<ServerResponse> {
        val projectPaths = runBlocking {
            notationMedia.scan()
        }

        val asMap = mutableMapOf<String, String>()

        for (e in projectPaths) {
            asMap[e.key.relativeLocation] = e.value.encode()
        }

        return ServerResponse
                .ok()
                .body(Mono.just(asMap))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun notation(serverRequest: ServerRequest): Mono<ServerResponse> {
        val notationPrefix = "/notation/"
        val requestSuffix = serverRequest.path().substring(notationPrefix.length)

        val notationPath = ProjectPath(requestSuffix)
        val notationBytes = runBlocking {
            notationMedia.read(notationPath)
        }

        val notationText = IoUtils.utf8ToString(notationBytes)

        val responseBuilder = ServerResponse.ok()

        return if (notationText.isEmpty()) {
            responseBuilder.build()
        }
        else {
            responseBuilder.body(Mono.just(notationText))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun commandEditParameter(serverRequest: ServerRequest): Mono<ServerResponse> {
        val objectName: String = serverRequest
                .queryParam("name")
                .orElseThrow { IllegalArgumentException("object name required") }

        val parameterPath: String = serverRequest
                .queryParam("parameter")
                .orElseThrow { IllegalArgumentException("parameter path required") }

        val valueYaml: String = serverRequest
                .queryParam("value")
                .orElseThrow { IllegalArgumentException("parameter value required") }

        val value = yamlParser.parseParameter(valueYaml)

        val command = EditParameterCommand(
                objectName, parameterPath, value)

        val digest = applyCommand(command)

        return ServerResponse
                .ok()
                .body(Mono.just(digest.encode()))
    }


    fun commandAddObject(serverRequest: ServerRequest): Mono<ServerResponse> {
        val projectPath: String = serverRequest
                .queryParam("path")
                .orElseThrow { IllegalArgumentException("project path required") }

        val objectName: String = serverRequest
                .queryParam("name")
                .orElseThrow { IllegalArgumentException("object name required") }

        val objectBody: String = serverRequest
                .queryParam("body")
                .orElseThrow { IllegalArgumentException("object body required") }

        val notation = yamlParser.parseObject(objectBody)

        val command = AddObjectCommand(
                ProjectPath(projectPath), objectName, notation)

        val digest = applyCommand(command)

        return ServerResponse
                .ok()
                .body(Mono.just(digest.encode()))
    }


    fun commandRemoveObject(serverRequest: ServerRequest): Mono<ServerResponse> {
        val objectName: String = serverRequest
                .queryParam("name")
                .orElseThrow { IllegalArgumentException("object name required") }

        val command = RemoveObjectCommand(objectName)

        val digest = applyCommand(command)

        return ServerResponse
                .ok()
                .body(Mono.just(digest.encode()))
    }


    fun commandShiftObject(serverRequest: ServerRequest): Mono<ServerResponse> {
        val objectName: String = serverRequest
                .queryParam("name")
                .orElseThrow { IllegalArgumentException("object name required") }

        val indexInPackage: Int = serverRequest
                .queryParam("index")
                .flatMap { Optional.ofNullable(Ints.tryParse(it)) }
                .orElseThrow { IllegalArgumentException("index number required") }

        val command = ShiftObjectCommand(objectName, indexInPackage)

        val digest = applyCommand(command)

        return ServerResponse
                .ok()
                .body(Mono.just(digest.encode()))
    }


    fun commandRenameObject(serverRequest: ServerRequest): Mono<ServerResponse> {
        val objectName: String = serverRequest
                .queryParam("name")
                .orElseThrow { IllegalArgumentException("object name required") }

        val newName = serverRequest
                .queryParam("to")
                .orElseThrow { IllegalArgumentException("to name required") }

        val command = RenameObjectCommand(objectName, newName)

        val digest = applyCommand(command)

        return ServerResponse
                .ok()
                .body(Mono.just(digest.encode()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun applyCommand(command: ProjectCommand): Digest {
        return runBlocking {
            repository.apply(command)
            repository.digest()
        }
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
                val body = Resources.toByteArray(resourceUrl)
                println("%%%%% read resource: ${resourceLocation.path}")
                return body
            }
            catch (ignored: Exception) {}
        }

        for (root in resourceDirectories) {
            val candidate = root.resolve(relativePath)
            if (! Files.exists(candidate)) {
                println("%%%%% no file at: ${candidate.toAbsolutePath()}")
                continue
            }

            val body = Files.readAllBytes(candidate)
            println("%%%%% read file: ${candidate.toAbsolutePath()}")
            return body
        }

        println("%%%%% not read: ${relativePath}")
        return null
    }
}