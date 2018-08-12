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
import tech.kzen.auto.common.api.AutoAction
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.lib.common.context.ObjectGraphCreator
import tech.kzen.lib.common.context.ObjectGraphDefiner
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
                "css",
                "ico")
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun scan(serverRequest: ServerRequest): Mono<ServerResponse> {
        val projectPaths = runBlocking {
            ServerContext.notationMedia.scan()
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
            ServerContext.notationMedia.read(notationPath)
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

        val value = ServerContext.yamlParser.parseParameter(valueYaml)

        return applyAndDigest(
                EditParameterCommand(objectName, parameterPath, value))
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

        val notation = ServerContext.yamlParser.parseObject(objectBody)

        return applyAndDigest(
                AddObjectCommand(ProjectPath(projectPath), objectName, notation))
    }


    fun commandRemoveObject(serverRequest: ServerRequest): Mono<ServerResponse> {
        val objectName: String = serverRequest
                .queryParam("name")
                .orElseThrow { IllegalArgumentException("object name required") }

        return applyAndDigest(
                RemoveObjectCommand(objectName))
    }


    fun commandShiftObject(serverRequest: ServerRequest): Mono<ServerResponse> {
        val objectName: String = serverRequest
                .queryParam("name")
                .orElseThrow { IllegalArgumentException("object name required") }

        val indexInPackage: Int = serverRequest
                .queryParam("index")
                .flatMap { Optional.ofNullable(Ints.tryParse(it)) }
                .orElseThrow { IllegalArgumentException("index number required") }

        return applyAndDigest(
                ShiftObjectCommand(objectName, indexInPackage))
    }


    fun commandRenameObject(serverRequest: ServerRequest): Mono<ServerResponse> {
        val objectName: String = serverRequest
                .queryParam("name")
                .orElseThrow { IllegalArgumentException("object name required") }

        val newName = serverRequest
                .queryParam("to")
                .orElseThrow { IllegalArgumentException("to name required") }

        return applyAndDigest(
                RenameObjectCommand(objectName, newName))
    }


    fun applyAndDigest(command: ProjectCommand): Mono<ServerResponse> {
        val digest = applyCommand(command)

        return ServerResponse
                .ok()
                .body(Mono.just(digest.encode()))
    }


    fun applyCommand(command: ProjectCommand): Digest {
        return runBlocking {
            val event = ServerContext.repository.apply(command)

            // TODO: consolidate with CommandBus
            ServerContext.modelManager.onEvent(event)

            ServerContext.repository.digest()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun actionPerform(serverRequest: ServerRequest): Mono<ServerResponse> {
        val objectName: String = serverRequest
                .queryParam("name")
                .orElseThrow { IllegalArgumentException("object name required") }

        runBlocking {
            val projectModel = ServerContext.modelManager.projectModel()

            val graphDefinition = ObjectGraphDefiner.define(
                    projectModel.projectNotation, projectModel.graphMetadata)

            val objectGraph = ObjectGraphCreator.createGraph(
                    graphDefinition, projectModel.graphMetadata)

            val instance = objectGraph.get(objectName)

            val action = instance as AutoAction

            action.perform()
        }

        return ServerResponse
                .ok()
                .body(Mono.just("success"))
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