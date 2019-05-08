package tech.kzen.auto.client.service

import tech.kzen.auto.client.service.rest.*
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowManager
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionLoop
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionManager
import tech.kzen.auto.common.service.GraphStructureManager
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

    private val restNotationMedia: NotationMedia = ClientRestNotationMedia(restClient)
    val notationMediaCache = MapNotationMedia()

    val notationParser: NotationParser = YamlNotationParser()

    val notationMetadataReader = NotationMetadataReader()

    private val clientRepository = NotationRepository(
            notationMediaCache, notationParser, notationMetadataReader)

    val modelManager = GraphStructureManager(
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
            modelManager,
            executionManager)

    val insertionManager = InsertionManager()
    val executionIntent = ExecutionIntent()

    val navigationManager = NavigationManager()

    private val clientRestVisualDataflowProvider = ClientRestVisualDataflowProvider(
            restClient)

    val visualDataflowManager = VisualDataflowManager(
            clientRestVisualDataflowProvider)


    //-----------------------------------------------------------------------------------------------------------------
    fun init() {
//        console.log("starting with baseUrl: ", baseUrl)

        val kzenAutoJs = js("require('kzen-auto-js.js')")
//        console.log("kzenAutoJs", kzenAutoJs)
        ModuleRegistry.add(kzenAutoJs)

        async {
            navigationManager.postConstruct(commandBus)

            modelManager.observe(executionManager)
            modelManager.observe(visualDataflowManager)
            executionManager.observe(executionLoop)
        }
    }
}