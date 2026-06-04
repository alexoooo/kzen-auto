package tech.kzen.auto.client.service

//import tech.kzen.auto.client.codegen.KzenAutoJsModule
//import tech.kzen.auto.common.paradigm.imperative.service.ExecutionLoop
//import tech.kzen.auto.common.paradigm.imperative.service.ExecutionRepository
import kotlinx.browser.window
import tech.kzen.auto.client.codegen.KzenAutoJsModule
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.ExecutionIntentGlobal
import tech.kzen.auto.client.service.global.InsertionGlobal
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.service.logic.ClientLogicGlobal
import tech.kzen.auto.client.service.rest.*
import tech.kzen.auto.client.wrap.iconify.IconLoader
import tech.kzen.auto.common.codegen.KzenAutoCommonModule
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowLoop
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowRepository
import tech.kzen.lib.common.codegen.KzenLibCommonModule
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.media.SeededNotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.common.service.store.DirectGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.platform.ClassName


class ClientContext private constructor() {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        init {
            KzenLibCommonModule.register()
            KzenAutoCommonModule.register()
            KzenAutoJsModule.register()
        }


        // Construction is self-initializing: callers get a ready context (observers wired, seeded
        // media scanned, stable ids seeded) rather than having to remember a separate init() step.
        suspend fun create(): ClientContext {
            return ClientContext().also { it.initAsync() }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    val baseUrl = window.location.pathname.substringBeforeLast("/")
    val restClient = ClientRestApi(baseUrl)

    private val restNotationMedia: NotationMedia = ClientRestNotationMedia(restClient)

    val notationParser: NotationParser = YamlNotationParser()

    private val notationMetadataReader = NotationMetadataReader()

    val graphDefiner = GraphDefiner
    val graphCreator = GraphCreator
    val notationReducer = NotationReducer

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
            visualDataflowLoop)

    val clientLogicGlobal = ClientLogicGlobal(
        restClient)

    val objectStableMapper = ObjectStableMapper()

    val clientStateGlobal = ClientStateGlobal()


    init {
        // Register the on-demand Iconify loader before any <Icon> renders.
        IconLoader.install(baseUrl)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Runtime services exposed to @Service constructor parameters of graph-instantiated objects,
    // keyed by the type each consumer declares. ClassName literals because KClass.qualifiedName
    // is not available in Kotlin/JS; each must match the FQN that KSP records for the declared
    // @Service parameter type.
    val graphEnvironment: GraphEnvironment by lazy {
        GraphEnvironment.builder()
            .put(ClassName("tech.kzen.lib.common.service.store.MirroredGraphStore"), mirroredGraphStore)
            .put(ClassName("tech.kzen.lib.common.service.store.normal.ObjectStableMapper"), objectStableMapper)
            .put(ClassName("tech.kzen.lib.common.service.parse.NotationParser"), notationParser)
            .put(ClassName("tech.kzen.auto.client.service.global.ClientStateGlobal"), clientStateGlobal)
            .put(ClassName("tech.kzen.auto.client.service.global.NavigationGlobal"), navigationGlobal)
            .put(ClassName("tech.kzen.auto.client.service.global.InsertionGlobal"), insertionGlobal)
            .put(ClassName("tech.kzen.auto.client.service.global.ExecutionIntentGlobal"), executionIntentGlobal)
            .put(ClassName("tech.kzen.auto.client.service.logic.ClientLogicGlobal"), clientLogicGlobal)
            .put(ClassName("tech.kzen.auto.client.service.rest.ClientRestApi"), restClient)
            .put(ClassName("tech.kzen.auto.client.service.rest.ClientRestTaskRepository"), clientRestTaskRepository)
            .put(ClassName("tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowRepository"), visualDataflowRepository)
            .put(ClassName("tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowLoop"), visualDataflowLoop)
            .build()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun initAsync() {
        // Best-effort: fetch always-visible icons up front so the first paint has them.
        IconLoader.preload(baseUrl)

        navigationGlobal.postConstruct(mirroredGraphStore)

//        mirroredGraphStore.observe(executionRepository)
        mirroredGraphStore.observe(visualDataflowRepository)

//        executionRepository.observe(executionLoop)
        visualDataflowRepository.observe(visualDataflowLoop)

        clientLogicGlobal.init()

        // NB: pre-load, otherwise can have race condition
        seededNotationMedia.scan()

        // Seed before observe — observing first would let lazy id generation race with the seed
        objectStableMapper.seed(restClient.objectStableMapperSnapshot())
        mirroredGraphStore.observe(objectStableMapper)

        clientStateGlobal.postConstruct(
                navigationGlobal, directGraphStore, clientLogicGlobal)
    }
}
