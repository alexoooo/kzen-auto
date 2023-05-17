package tech.kzen.auto.client.service

import kotlinx.browser.window
import tech.kzen.auto.client.codegen.KzenAutoJsModule
//import tech.kzen.auto.client.codegen.KzenAutoJsModule
import tech.kzen.auto.client.service.global.ExecutionIntentGlobal
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.logic.ClientLogicGlobal
import tech.kzen.auto.client.service.rest.*
import tech.kzen.auto.common.codegen.KzenAutoCommonModule
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowLoop
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowRepository
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionLoop
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionRepository
import tech.kzen.lib.common.codegen.KzenLibCommonModule
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

    val executionRepository = ExecutionRepository(
            restExecutionInitializer,
            restActionExecutor)

    val executionLoop = ExecutionLoop(
            mirroredGraphStore,
            executionRepository,
            125)

    val insertionGlobal = InsertionGlobal()
    val executionIntentGlobal = ExecutionIntentGlobal()

    private val clientRestVisualDataflowProvider = ClientRestVisualDataflowProvider(
            restClient)

    val visualDataflowRepository = VisualDataflowRepository(
            clientRestVisualDataflowProvider)

    val visualDataflowLoop = VisualDataflowLoop(
            mirroredGraphStore,
            visualDataflowRepository,
            250,
            200)


    val clientRestTaskRepository = ClientRestTaskRepository(
        restClient)


    val navigationGlobal = NavigationGlobal(
            executionLoop,
            visualDataflowLoop)

    val clientLogicGlobal = ClientLogicGlobal(
        restClient)

    val sessionGlobal = SessionGlobal()


    //-----------------------------------------------------------------------------------------------------------------
    fun init() {
        KzenLibCommonModule.register()
        KzenAutoCommonModule.register()
        KzenAutoJsModule.register()
    }


    suspend fun initAsync() {
        navigationGlobal.postConstruct(mirroredGraphStore)

        mirroredGraphStore.observe(executionRepository)
        mirroredGraphStore.observe(visualDataflowRepository)

        executionRepository.observe(executionLoop)
        visualDataflowRepository.observe(visualDataflowLoop)

        clientLogicGlobal.init()

        // NB: pre-load, otherwise can have race condition
        seededNotationMedia.scan()

        sessionGlobal.postConstruct(
                navigationGlobal, directGraphStore, clientLogicGlobal, restClient, executionRepository)
    }
}