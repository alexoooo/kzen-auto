package tech.kzen.auto.server.service

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.codegen.KzenAutoCommonModule
import tech.kzen.auto.common.paradigm.dataflow.service.active.ActiveDataflowRepository
import tech.kzen.auto.common.paradigm.dataflow.service.active.ActiveVisualProvider
import tech.kzen.auto.common.paradigm.dataflow.service.format.DataflowMessageInspector
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowRepository
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionRepository
import tech.kzen.auto.common.service.GraphInstanceCreator
import tech.kzen.auto.server.codegen.KzenAutoJvmModule
import tech.kzen.auto.server.objects.plugin.PluginProcessorDefinitionRepository
import tech.kzen.auto.server.objects.report.*
import tech.kzen.auto.server.objects.report.pipeline.calc.CalculatedColumnEval
import tech.kzen.auto.server.objects.report.pipeline.input.parse.csv.CsvProcessorDefiner
import tech.kzen.auto.server.objects.report.pipeline.input.parse.text.TextProcessorDefiner
import tech.kzen.auto.server.objects.report.pipeline.input.parse.tsv.TsvProcessorDefiner
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.service.compile.EmbeddedKotlinCompiler
import tech.kzen.auto.server.service.exec.EmptyExecutionInitializer
import tech.kzen.auto.server.service.exec.ModelActionExecutor
import tech.kzen.auto.server.service.exec.ModelDetachedExecutor
import tech.kzen.auto.server.service.exec.ModelTaskRepository
import tech.kzen.auto.server.service.plugin.DefinerDefinitionRepository
import tech.kzen.auto.server.service.plugin.MultiDefinitionRepository
import tech.kzen.auto.server.service.webdriver.WebDriverContext
import tech.kzen.auto.server.service.webdriver.WebDriverInstaller
import tech.kzen.auto.server.service.webdriver.WebDriverOptionDao
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.codegen.KzenLibCommonModule
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.media.MultiNotationMedia
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.common.service.store.DirectGraphStore
import tech.kzen.lib.server.notation.FileNotationMedia
import tech.kzen.lib.server.notation.locate.GradleLocator


object ServerContext {
    //-----------------------------------------------------------------------------------------------------------------
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

    val graphStore = DirectGraphStore(
            notationMedia,
            yamlParser,
            notationMetadataReader,
            graphDefiner,
            notationReducer)

    val actionExecutor = ModelActionExecutor(
            graphStore, graphCreator)

    val detachedExecutor = ModelDetachedExecutor(
            graphStore, graphCreator)

    val executionRepository = ExecutionRepository(
            EmptyExecutionInitializer,
            actionExecutor)

    val modelTaskRepository = ModelTaskRepository(
        graphStore, graphCreator)

    private val graphInstanceCreator = GraphInstanceCreator(
            graphStore, graphCreator)

    private val dataflowMessageInspector = DataflowMessageInspector()

    private val activeDataflowRepository = ActiveDataflowRepository(
            graphInstanceCreator,
            dataflowMessageInspector,
            graphStore)

    private val activeVisualProvider = ActiveVisualProvider(
            activeDataflowRepository)

    val visualDataflowRepository = VisualDataflowRepository(
            activeVisualProvider)

    val workUtils = WorkUtils.sibling
    val reportWorkPool = ReportWorkPool(workUtils)

    val kotlinCompiler = EmbeddedKotlinCompiler()
    val cachedKotlinCompiler = CachedKotlinCompiler(kotlinCompiler, reportWorkPool, workUtils)
    val calculatedColumnEval = CalculatedColumnEval(cachedKotlinCompiler)

    val fileListingAction = FileListingAction()
    val reportRunAction = ReportRunAction(reportWorkPool)
    val filterIndex = FilterIndex(workUtils)
    val columnListingAction = ColumnListingAction(filterIndex)


    private val basicDefinitionRepository = DefinerDefinitionRepository(listOf(
        CsvProcessorDefiner(),
        TsvProcessorDefiner(),
        TextProcessorDefiner()))

    private val pluginProcessorDefinitionRepository = PluginProcessorDefinitionRepository(
         graphStore, graphDefiner, graphCreator)

    val definitionRepository = MultiDefinitionRepository(listOf(
        basicDefinitionRepository, pluginProcessorDefinitionRepository))


    //-----------------------------------------------------------------------------------------------------------------
    private val downloadClient = DownloadClient()

    val webDriverRepo = WebDriverOptionDao()
    val webDriverInstaller = WebDriverInstaller(downloadClient)
    val webDriverContext = WebDriverContext()


    //-----------------------------------------------------------------------------------------------------------------
    init {
        KzenLibCommonModule.register()
        KzenAutoCommonModule.register()
        KzenAutoJvmModule.register()

        downloadClient.initialize()

        runBlocking {
            graphStore.observe(executionRepository)
            graphStore.observe(activeDataflowRepository)
            graphStore.observe(visualDataflowRepository)
            graphStore.observe(modelTaskRepository)
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