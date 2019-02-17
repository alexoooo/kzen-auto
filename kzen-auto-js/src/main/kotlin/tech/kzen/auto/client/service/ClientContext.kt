package tech.kzen.auto.client.service

import tech.kzen.auto.client.service.exec.ExecutionIntent
import tech.kzen.auto.client.service.rest.ClientRestActionExecutor
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.service.rest.ClientRestExecutionInitializer
import tech.kzen.auto.client.service.rest.RestNotationMedia
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.service.ExecutionLoop
import tech.kzen.auto.common.service.ExecutionManager
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.structure.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.structure.notation.format.YamlNotationParser
import tech.kzen.lib.common.structure.notation.io.NotationMedia
import tech.kzen.lib.common.structure.notation.io.NotationParser
import tech.kzen.lib.common.structure.notation.io.common.MapNotationMedia
import tech.kzen.lib.common.structure.notation.repo.NotationRepository
import tech.kzen.lib.platform.client.ModuleRegistry
import kotlin.browser.window


object ClientContext {
    //-----------------------------------------------------------------------------------------------------------------
    val baseUrl = window.location.pathname.substringBeforeLast("/")
    val restClient = ClientRestApi(baseUrl)

    private val restNotationMedia: NotationMedia = RestNotationMedia(restClient)
    val notationMediaCache = MapNotationMedia()

    val notationParser: NotationParser = YamlNotationParser()

    val notationMetadataReader = NotationMetadataReader()

    private val clientRepository = NotationRepository(
            notationMediaCache, notationParser, notationMetadataReader)

    val modelManager = ModelManager(
            notationMediaCache,
            clientRepository,
            restNotationMedia,
            notationMetadataReader)

    val commandBus = CommandBus(
            clientRepository,
            modelManager,
            restClient,
            notationParser)


    private val restExecutionInitializer = ClientRestExecutionInitializer(
            restClient)

    private val restActionExecutor = ClientRestActionExecutor(
            restClient)

    val executionManager = ExecutionManager(
            restExecutionInitializer,
            restActionExecutor)

    val executionLoop = ExecutionLoop(
            executionManager)

    val insertionManager = InsertionManager()
    val executionIntent = ExecutionIntent()


    //-----------------------------------------------------------------------------------------------------------------
    fun init() {
        val kzenAutoJs = js("require('kzen-auto-js.js')")
//        console.log("kzenAutoJs", kzenAutoJs)
        ModuleRegistry.add(kzenAutoJs)

        async {
            modelManager.observe(executionManager)
            executionManager.subscribe(executionLoop)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun foo() {
//
//        val notation = context.repository.notation()
//        val autoNotation = NotationConventions.autoNotation(notation)
//
//        val autoMetadata = context.notationMetadataReader.read(autoNotation)
//
//        val graphDefinition = ObjectGraphDefiner.define(
//                autoNotation, autoMetadata)
//
//        val autoGraph = ObjectGraphCreator
//                .createGraph(graphDefinition, autoMetadata)
//    }
}