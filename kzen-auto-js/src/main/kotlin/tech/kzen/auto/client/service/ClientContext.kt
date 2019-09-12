package tech.kzen.auto.client.service

import tech.kzen.auto.client.service.rest.*
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowLoop
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowManager
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionLoop
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionManager
import tech.kzen.auto.common.service.GraphStructureManager
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.context.NotationRepository
import tech.kzen.lib.common.service.media.MapNotationMedia
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.service.parse.YamlNotationParser
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

    val graphDefiner = GraphDefiner()
    val graphCreator = GraphCreator()
    val notationReducer = NotationReducer()

    private val clientRepository = NotationRepository(
            notationMediaCache,
            notationParser,
            notationMetadataReader,
            graphDefiner,
            notationReducer)

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
            executionManager,
            250)

    val insertionManager = InsertionManager()
    val executionIntent = ExecutionIntent()

    private val clientRestVisualDataflowProvider = ClientRestVisualDataflowProvider(
            restClient)

    val visualDataflowManager = VisualDataflowManager(
            clientRestVisualDataflowProvider)

    val visualDataflowLoop = VisualDataflowLoop(
            modelManager,
            visualDataflowManager,
            250,
            200)

    val navigationManager = NavigationManager(
            executionLoop,
            visualDataflowLoop)


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
            visualDataflowManager.observe(visualDataflowLoop)
        }
    }
}