package tech.kzen.auto.server.api

import com.google.common.io.MoreFiles
import com.google.common.io.Resources
import com.google.common.primitives.Ints
import kotlinx.coroutines.experimental.runBlocking
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
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.IoUtils
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.stream.Collectors


@Component
class RestHandler {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val classPathRoots = listOf(
                URI("classpath:/public/"))

        val resourceDirectories = discoverResourceDirectories()

        val allowedExtensions = listOf(
                "html",
                "js",
                "css",
                "svg",
                "png",
                "ico")


        private const val jvmSuffix = "-jvm"

        private fun discoverResourceDirectories(): List<Path> {
            val builder = mutableListOf<Path>()

            // TODO: consolidate with GradleLocator?

            val projectRoot =
                    if (Files.exists(Paths.get("src"))) {
                        ".."
                    }
                    else {
                        "."
                    }

            val projectName = Files.list(Paths.get(projectRoot)).use { files ->
                val list = files.collect(Collectors.toList())

                val jvmModule = list.firstOrNull { it.fileName.toString().endsWith(jvmSuffix)}
                if (jvmModule == null) {
                    // ?: throw IllegalStateException("No -jvm module: - $list")
                    jvmModule
                }
                else {
                    val filename = jvmModule.fileName.toString()

                    filename.substring(0 until filename.length - jvmSuffix.length)
                }
            }

            if (projectName != null) {
                // IntelliJ and typical commandline working dir is project root
                builder.add(Paths.get("$projectName-jvm/src/main/resources/public/"))
                builder.add(Paths.get("$projectName-js/build/dist/"))

                // Eclipse and Gradle default active working directory is the module
                builder.add(Paths.get("src/main/resources/public/"))
                builder.add(Paths.get("../$projectName-js/build/dist/"))
            }
            else {
                builder.add(Paths.get("static/"))
            }

            return builder
        }
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
        val projectPath: ProjectPath = serverRequest
                .queryParam("path")
                .map { ProjectPath(it) }
                .orElseThrow { IllegalArgumentException("project path required") }

        val objectName: String = serverRequest
                .queryParam("name")
                .orElseThrow { IllegalArgumentException("object name required") }

        val objectBody: String = serverRequest
                .queryParam("body")
                .orElseThrow { IllegalArgumentException("object body required") }

        val notation = ServerContext.yamlParser.parseObject(objectBody)

        return applyAndDigest(
                AddObjectCommand(projectPath, objectName, notation))
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


    fun commandCreatePackge(serverRequest: ServerRequest): Mono<ServerResponse> {
        val projectPath: ProjectPath = serverRequest
                .queryParam("path")
                .map { ProjectPath(it) }
                .orElseThrow { IllegalArgumentException("project path required") }

        return applyAndDigest(
                CreatePackageCommand(projectPath = projectPath))
    }


    fun commandDeletePackge(serverRequest: ServerRequest): Mono<ServerResponse> {
        val projectPath: ProjectPath = serverRequest
                .queryParam("path")
                .map { ProjectPath(it) }
                .orElseThrow { IllegalArgumentException("project path required") }

        return applyAndDigest(
                DeletePackageCommand(projectPath = projectPath))
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

            // TODO: consolidate with CommandBus?
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

        val responseBuilder = ServerResponse.ok()
        return if (bytes.isEmpty()) {
            responseBuilder.build()
        }
        else {
            responseBuilder.body(Mono.just(bytes))
        }
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
                val relativeResource = resourceLocation.path.substring(1)

//                println("%%%%% looking at resource: $relativeResource")
                val resourceUrl = Resources.getResource(relativeResource)
                val body = Resources.toByteArray(resourceUrl)
//                println("%%%%% read resource: relativePath")
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
//            println("%%%%% read file: ${candidate.toAbsolutePath()}")
            return body
        }

        println("%%%%% not read: ${relativePath}")
        return null
    }
}