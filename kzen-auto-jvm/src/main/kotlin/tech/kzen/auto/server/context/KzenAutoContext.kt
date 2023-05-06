package tech.kzen.auto.server.context

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.codegen.KzenAutoCommonModule
import tech.kzen.auto.common.paradigm.dataflow.service.active.ActiveDataflowRepository
import tech.kzen.auto.common.paradigm.dataflow.service.active.ActiveVisualProvider
import tech.kzen.auto.common.paradigm.dataflow.service.format.DataflowMessageInspector
import tech.kzen.auto.common.paradigm.dataflow.service.visual.VisualDataflowRepository
import tech.kzen.auto.common.paradigm.imperative.service.ExecutionRepository
import tech.kzen.auto.common.service.GraphInstanceCreator
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.server.api.RestHandler
import tech.kzen.auto.server.codegen.KzenAutoJvmModule
import tech.kzen.auto.server.objects.plugin.PluginReportDefinitionRepository
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumnEval
import tech.kzen.auto.server.objects.report.exec.input.parse.csv.CsvReportDefiner
import tech.kzen.auto.server.objects.report.exec.input.parse.text.TextReportDefiner
import tech.kzen.auto.server.objects.report.exec.input.parse.tsv.TsvReportDefiner
import tech.kzen.auto.server.objects.report.service.ColumnListingAction
import tech.kzen.auto.server.objects.report.service.FileListingAction
import tech.kzen.auto.server.objects.report.service.FilterIndex
import tech.kzen.auto.server.objects.report.service.ReportWorkPool
import tech.kzen.auto.server.service.DownloadClient
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.service.compile.ScriptKotlinCompiler
import tech.kzen.auto.server.service.exec.EmptyExecutionInitializer
import tech.kzen.auto.server.service.exec.ModelActionExecutor
import tech.kzen.auto.server.service.exec.ModelDetachedExecutor
import tech.kzen.auto.server.service.exec.ModelTaskRepository
import tech.kzen.auto.server.service.plugin.HostReportDefinitionRepository
import tech.kzen.auto.server.service.plugin.MultiDefinitionRepository
import tech.kzen.auto.server.service.plugin.ReportDefinitionRepository
import tech.kzen.auto.server.service.v1.impl.ServerLogicController
import tech.kzen.auto.server.service.webdriver.WebDriverContext
import tech.kzen.auto.server.service.webdriver.WebDriverInstaller
import tech.kzen.auto.server.service.webdriver.WebDriverOptionDao
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.codegen.KzenLibCommonModule
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.media.ReadWriteNotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.common.service.store.DirectGraphStore
import tech.kzen.lib.server.notation.ClasspathNotationMedia
import tech.kzen.lib.server.notation.FileNotationMedia
import tech.kzen.lib.server.notation.locate.GradleLocator


class KzenAutoContext(
    val config: KzenAutoConfig
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        init {
            KzenLibCommonModule.register()
            KzenAutoCommonModule.register()
            KzenAutoJvmModule.register()
        }

        private var global: KzenAutoContext? = null

        fun setGlobal(context: KzenAutoContext) {
            check(global == null) { "Already set" }
            global = context
        }

        fun clearGlobal() {
            check(global != null) { "Not set" }
            global = null
        }


        /**
         * Kzen implements a dynamic dependency injection container, but it can be useful to separately
         *  perform static dependency injection (KzenAutoContext) as a bootstrap.
         * This method allows Kzen-managed instances to access the KzenAutoContext.
         */
        fun global(): KzenAutoContext {
            return global!!
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val notationMetadataReader = NotationMetadataReader()

    private val fileLocator = GradleLocator()
    private val fileMedia = FileNotationMedia(
        fileLocator, require = listOf(AutoConventions.autoMainDocumentNesting))

    private val classpathNotationMedia = ClasspathNotationMedia(
        exclude = listOf(AutoConventions.autoMainDocumentNesting))

    val notationMedia: NotationMedia = ReadWriteNotationMedia(
        fileMedia, classpathNotationMedia
    )

    val yamlParser = YamlNotationParser()

    val graphDefiner = GraphDefiner()
    val graphCreator = GraphCreator()
    val notationReducer = NotationReducer()

    val graphStore = DirectGraphStore(
            notationMedia,
            yamlParser,
            notationMetadataReader,
            graphDefiner,
            notationReducer
    )

    val actionExecutor = ModelActionExecutor(
            graphStore, graphCreator
    )

    val detachedExecutor = ModelDetachedExecutor(
            graphStore, graphCreator
    )

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
            graphStore
    )

    private val activeVisualProvider = ActiveVisualProvider(
            activeDataflowRepository)

    val visualDataflowRepository = VisualDataflowRepository(
            activeVisualProvider)

    val workUtils = WorkUtils.sibling
    val reportWorkPool = ReportWorkPool(workUtils)

//    val kotlinCompiler = EmbeddedKotlinCompiler()
    val kotlinCompiler = ScriptKotlinCompiler()
    val cachedKotlinCompiler = CachedKotlinCompiler(kotlinCompiler, workUtils)
    val calculatedColumnEval = CalculatedColumnEval(cachedKotlinCompiler)

    val fileListingAction = FileListingAction()
    val filterIndex = FilterIndex(workUtils)
    val columnListingAction = ColumnListingAction(filterIndex)


    private val basicDefinitionRepository = HostReportDefinitionRepository(listOf(
        CsvReportDefiner(),
        TsvReportDefiner(),
        TextReportDefiner()))

    private val pluginProcessorDefinitionRepository = PluginReportDefinitionRepository(
         graphStore, graphDefiner, graphCreator)

    val definitionRepository: ReportDefinitionRepository = MultiDefinitionRepository(listOf(
        basicDefinitionRepository, pluginProcessorDefinitionRepository))


    val serverLogicController = ServerLogicController(
        graphStore, graphCreator)

    val restHandler = RestHandler(
        notationMedia,
        yamlParser,
        graphStore,
        executionRepository,
        detachedExecutor,
        visualDataflowRepository,
        modelTaskRepository,
        serverLogicController)


    //-----------------------------------------------------------------------------------------------------------------
    private val downloadClient = DownloadClient()

    val webDriverRepo = WebDriverOptionDao()
    val webDriverInstaller = WebDriverInstaller(downloadClient)
    val webDriverContext = WebDriverContext()


    //-----------------------------------------------------------------------------------------------------------------
    fun init() {
        runBlocking {
            graphStore.observe(executionRepository)
            graphStore.observe(activeDataflowRepository)
            graphStore.observe(visualDataflowRepository)
            graphStore.observe(modelTaskRepository)
        }
    }


    fun close() {
        webDriverContext.quit()
    }
}