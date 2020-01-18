package tech.kzen.auto.client.service

import tech.kzen.auto.client.service.rest.*
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowLoop
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowManager
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionLoop
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionManager
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.media.SeededNotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.common.service.store.DirectGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.platform.client.ModuleRegistry
import kotlin.browser.window


object ClientContext {
    //-----------------------------------------------------------------------------------------------------------------
    val baseUrl = window.location.pathname.substringBeforeLast("/")
    val restClient = ClientRestApi(baseUrl)

    private val restNotationMedia: NotationMedia = ClientRestNotationMedia(restClient)

    val notationParser: NotationParser = YamlNotationParser()

    val notationMetadataReader = NotationMetadataReader()

    val graphDefiner = GraphDefiner()
    val graphCreator = GraphCreator()
    val notationReducer = NotationReducer()

    val seededNotationMedia = SeededNotationMedia(
            restNotationMedia)

    private val directGraphStore = DirectGraphStore(
            seededNotationMedia,
            notationParser,
            notationMetadataReader,
            graphDefiner,
            notationReducer)

    private val remoteGraphStore = ClientRestGraphStore(
            restClient, notationParser)

    val mirroredGraphStore = MirroredGraphStore(
            directGraphStore, remoteGraphStore)

    private val restExecutionInitializer = ClientRestExecutionInitializer(
            restClient)

    private val restActionExecutor = ClientRestActionExecutor(
            restClient)

    val executionManager = ExecutionManager(
            restExecutionInitializer,
            restActionExecutor)

    val executionLoop = ExecutionLoop(
            mirroredGraphStore,
            executionManager,
            125)

    val insertionManager = InsertionManager()
    val executionIntent = ExecutionIntent()

    private val clientRestVisualDataflowProvider = ClientRestVisualDataflowProvider(
            restClient)

    val visualDataflowManager = VisualDataflowManager(
            clientRestVisualDataflowProvider)

    val visualDataflowLoop = VisualDataflowLoop(
            mirroredGraphStore,
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
    }


    suspend fun initAsync() {
        navigationManager.postConstruct(mirroredGraphStore)

        mirroredGraphStore.observe(executionManager)
        mirroredGraphStore.observe(visualDataflowManager)

        executionManager.observe(executionLoop)
        visualDataflowManager.observe(visualDataflowLoop)

        // NB: pre-load, otherwise can have race condition
        seededNotationMedia.scan()
    }
}