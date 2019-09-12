package tech.kzen.auto.server.service

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.paradigm.dataflow.service.active.ActiveDataflowManager
import tech.kzen.auto.common.paradigm.dataflow.service.active.ActiveVisualProvider
import tech.kzen.auto.common.paradigm.dataflow.service.format.DataflowMessageInspector
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowManager
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionManager
import tech.kzen.auto.common.service.GraphInstanceManager
import tech.kzen.auto.common.service.GraphStructureManager
import tech.kzen.auto.server.notation.BootNotationMedia
import tech.kzen.auto.server.service.imperative.EmptyExecutionInitializer
import tech.kzen.auto.server.service.imperative.ModelActionExecutor
import tech.kzen.auto.server.service.webdriver.WebDriverContext
import tech.kzen.auto.server.service.webdriver.WebDriverInstaller
import tech.kzen.auto.server.service.webdriver.WebDriverOptionDao
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.context.NotationRepository
import tech.kzen.lib.common.service.media.MapNotationMedia
import tech.kzen.lib.common.service.media.MultiNotationMedia
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.server.notation.FileNotationMedia
import tech.kzen.lib.server.notation.locate.GradleLocator


object ServerContext {
    //-----------------------------------------------------------------------------------------------------------------
    private val notationMediaCache = MapNotationMedia()
    private val notationMetadataReader = NotationMetadataReader()


    private val fileLocator = GradleLocator()
    private val fileMedia = FileNotationMedia(fileLocator)

    private val bootMedia = BootNotationMedia()

    val notationMedia: NotationMedia = MultiNotationMedia(listOf(
            fileMedia, bootMedia))

    val yamlParser = YamlNotationParser()

    val graphDefiner = GraphDefiner()
    val graphCreator = GraphCreator()
    val notationReducer = NotationReducer()

    val repository = NotationRepository(
            notationMedia,
            yamlParser,
            notationMetadataReader,
            graphDefiner,
            notationReducer)

    val graphStructureManager = GraphStructureManager(
            notationMediaCache,
            repository,
            notationMedia,
            notationMetadataReader)

    val actionExecutor = ModelActionExecutor(
            graphStructureManager, graphDefiner, graphCreator)

    val executionManager = ExecutionManager(
            EmptyExecutionInitializer,
            actionExecutor)


    private val graphInstanceManager = GraphInstanceManager(
            graphStructureManager, graphDefiner, graphCreator)

    private val dataflowMessageInspector = DataflowMessageInspector()

    private val activeDataflowManager = ActiveDataflowManager(
            graphInstanceManager,
            dataflowMessageInspector,
            graphStructureManager)

    private val activeVisualProvider = ActiveVisualProvider(
            activeDataflowManager)

    val visualDataflowManager = VisualDataflowManager(
            activeVisualProvider)


    //-----------------------------------------------------------------------------------------------------------------
    private val downloadManager = DownloadManager()

    val webDriverRepo = WebDriverOptionDao()
    val webDriverInstaller = WebDriverInstaller(downloadManager)
    val webDriverContext = WebDriverContext()


    //-----------------------------------------------------------------------------------------------------------------
    init {
        downloadManager.initialize()

        runBlocking {
            graphStructureManager.observe(executionManager)
            graphStructureManager.observe(graphInstanceManager)
            graphStructureManager.observe(activeDataflowManager)
            graphStructureManager.observe(visualDataflowManager)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun init() {
        // NB: trigger above init block, if not already triggered
    }

    fun close() {
        webDriverContext.quit()
    }
}