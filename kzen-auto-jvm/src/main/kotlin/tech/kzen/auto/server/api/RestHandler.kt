package tech.kzen.auto.server.api

import kotlinx.coroutines.experimental.runBlocking
import com.google.common.io.MoreFiles
import com.google.common.io.Resources
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import tech.kzen.lib.common.edit.AddObjectCommand
import tech.kzen.lib.common.edit.EditParameterCommand
import tech.kzen.lib.common.edit.ProjectAggregate
import tech.kzen.lib.common.edit.ProjectCommand
import tech.kzen.lib.common.notation.format.YamlNotationParser
import tech.kzen.lib.common.notation.io.flat.media.FallbackNotationMedia
import tech.kzen.lib.common.notation.model.*
import tech.kzen.lib.common.notation.scan.LiteralNotationScanner
import tech.kzen.lib.common.notation.scan.MultiNotationScanner
import tech.kzen.lib.common.notation.scan.NotationScanner
import tech.kzen.lib.common.util.IoUtils
import tech.kzen.lib.server.notation.*
import tech.kzen.lib.server.notation.locate.GradleLocator
import tech.kzen.lib.server.notation.scan.ClasspathNotationScanner
import tech.kzen.lib.server.notation.scan.DirectoryNotationScanner
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

    private val notationMedia = FallbackNotationMedia(listOf(
            FileNotationMedia(fileLocator),
            ClasspathNotationMedia()))

    private val notationScanner: NotationScanner = MultiNotationScanner(listOf(
        ClasspathNotationScanner(),
        DirectoryNotationScanner(fileLocator)))

    private val yamlParser = YamlNotationParser()


    //-----------------------------------------------------------------------------------------------------------------
    fun scan(serverRequest: ServerRequest): Mono<ServerResponse> {
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
        val notationPrefix = "/notation/"
        val requestSuffix = serverRequest.path().substring(notationPrefix.length)

        val notationPath = ProjectPath(requestSuffix)
        val notationBytes = runBlocking {
            notationMedia.read(notationPath)
        }

        val notationText = IoUtils.utf8ToString(notationBytes)

        return ServerResponse
                .ok()
                .body(Mono.just(notationText))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun commandEditParameter(serverRequest: ServerRequest): Mono<ServerResponse> {
        val objectName = serverRequest.queryParam("name")
                .orElseThrow { IllegalArgumentException("object name required") }

        val parameterPath = serverRequest.queryParam("parameter")
                .orElseThrow { IllegalArgumentException("parameter path required") }

        val valueYaml = serverRequest.queryParam("value")
                .orElseThrow { IllegalArgumentException("parameter value required") }

        val value = yamlParser.parseParameter(valueYaml)

        val command = EditParameterCommand(
                objectName, parameterPath, value)

        applyCommand(command)

        return ServerResponse
                .ok()
                .body(Mono.just("success"))
    }


    fun commandAddObject(serverRequest: ServerRequest): Mono<ServerResponse> {
        val objectName = serverRequest.queryParam("name")
                .orElseThrow { IllegalArgumentException("object name required") }

        val objectType = serverRequest.queryParam("type")
                .orElseThrow { IllegalArgumentException("object type (parent) required") }

        val projectPath = serverRequest.queryParam("path")
                .orElseThrow { IllegalArgumentException("project path required") }

        val notation = ObjectNotation(mapOf(
                ParameterConventions.isParameter to ScalarParameterNotation(objectType)))

        val command = AddObjectCommand(
                ProjectPath(projectPath), objectName, notation)

        applyCommand(command)

        return ServerResponse
                .ok()
                .body(Mono.just("success"))
    }


    fun applyCommand(command: ProjectCommand) {
        runBlocking {
            val packageBytes = mutableMapOf<ProjectPath, ByteArray>()
            val packages = mutableMapOf<ProjectPath, PackageNotation>()
            for (projectPath in notationScanner.scan()) {
                val body = notationMedia.read(projectPath)
                packageBytes[projectPath] = body

                val packageNotation = yamlParser.parse(body)
                packages[projectPath] = packageNotation
            }
            val projectNotation = ProjectNotation(packages)

            val project = ProjectAggregate(projectNotation)

            val event = project.apply(command)

            val updatedNotation = event.state

            for (updatedPackage in updatedNotation.packages) {
                if (packages[updatedPackage.key] == updatedPackage.value) {
                    continue
                }

                val previousBody = packageBytes[updatedPackage.key]!!
                val updatedBody = yamlParser.deparse(updatedPackage.value, previousBody)

                notationMedia.write(updatedPackage.key, updatedBody)
            }
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