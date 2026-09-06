package tech.kzen.auto.server.context

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.codegen.KzenAutoCommonModule
import tech.kzen.auto.common.service.ServiceEnvironmentValidation
import tech.kzen.auto.server.api.handler.DetachedActionHandler
import tech.kzen.auto.server.api.handler.FileListingHandler
import tech.kzen.auto.server.api.handler.LogicHandler
import tech.kzen.auto.server.api.handler.NotationQueryHandler
import tech.kzen.auto.server.api.handler.ObjectStableHandler
import tech.kzen.auto.server.api.handler.StorageHandler
import tech.kzen.auto.server.api.handler.TaskHandler
import tech.kzen.auto.server.api.handler.command.NotationCommandHandler
import tech.kzen.auto.server.codegen.KzenAutoJvmModule
import tech.kzen.auto.server.exec.job.JobTraceAddressRouting
import tech.kzen.auto.server.exec.report.ReportTraceAddressRouting
import tech.kzen.auto.server.objects.job.JobValidationCache
import tech.kzen.auto.server.objects.job.expression.JobExpressionCompiler
import tech.kzen.auto.server.objects.job.service.JobWorkPool
import tech.kzen.auto.server.objects.report.exec.calc.CalculatedColumnEval
import tech.kzen.auto.server.objects.report.exec.input.parse.csv.CsvReportDefiner
import tech.kzen.auto.server.objects.report.exec.input.parse.text.TextReportDefiner
import tech.kzen.auto.server.objects.report.exec.input.parse.tsv.TsvReportDefiner
import tech.kzen.auto.server.data.ColumnListingAction
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.data.FileListingAction
import tech.kzen.auto.server.data.ConfiguredDataOpener
import tech.kzen.auto.server.data.SchemaCache
import tech.kzen.auto.server.data.content.SequentialContentStack
import tech.kzen.auto.server.data.content.local.LocalDataContentProvider
import tech.kzen.auto.server.data.content.provider.DataContentProviderLookup
import tech.kzen.auto.server.data.read.ReaderCapabilityRegistry
import tech.kzen.auto.server.data.read.detection.AutomaticFormatResolver
import tech.kzen.auto.server.data.read.detection.DetectionSampleAcquirer
import tech.kzen.auto.server.data.format.SourceFormatResolutionBudgetFactory
import tech.kzen.auto.server.objects.datasource.format.ConfiguredRecordFormatLookup
import tech.kzen.auto.server.objects.datasource.format.ConfiguredRecordFormatRegistry
import tech.kzen.auto.server.objects.report.service.ReportWorkPool
import tech.kzen.auto.server.objects.script.ScriptValidationCache
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.service.compile.KotlinSyntaxValidator
import tech.kzen.auto.server.service.compile.ScriptKotlinCompiler
import tech.kzen.auto.server.service.exec.GraphInstanceCache
import tech.kzen.auto.server.service.exec.ModelDetachedExecutor
import tech.kzen.auto.server.service.exec.ModelTaskRepository
import tech.kzen.auto.server.service.impl.ServerLogicController
import tech.kzen.auto.server.service.plugin.HostReportDefinitionRepository
import tech.kzen.auto.server.data.ReportDefinitionRepository
import tech.kzen.auto.server.service.storage.DirectoryStorageArea
import tech.kzen.auto.server.service.storage.SchemaCacheStorageArea
import tech.kzen.auto.server.service.storage.JobOutputStorageArea
import tech.kzen.auto.server.service.storage.ManagedStorageRegistry
import tech.kzen.auto.server.service.storage.ReportStorageArea
import tech.kzen.auto.server.service.storage.StorageLruEvictor
import tech.kzen.auto.server.service.target.TargetLocator
import org.slf4j.LoggerFactory
import tech.kzen.auto.server.context.runtime.KzenAutoRuntime
import tech.kzen.auto.server.context.runtime.WorkRootRegistry
import tech.kzen.auto.server.util.WorkUtils
import java.nio.file.Files
import tech.kzen.lib.common.codegen.KzenLibCommonModule
import tech.kzen.lib.common.reflect.GlobalMirror
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.media.LiteralNotationMedia
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.media.ReadWriteNotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.auto.common.objects.document.script.model.KzenAutoCodeReferenceRewriter
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.common.service.store.DirectGraphStore
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import tech.kzen.lib.platform.ClassName
import tech.kzen.auto.server.exec.RunEngineLogicTrace
import tech.kzen.lib.common.exec.logic.trace.LogicTrace
import tech.kzen.lib.server.notation.FileNotationMedia
import tech.kzen.lib.server.notation.locate.FileNotationLocator
import tech.kzen.lib.server.notation.locate.GradleLocator
import tech.kzen.lib.server.reflect.ReflectiveClassMirror
import java.lang.AutoCloseable
import java.nio.file.Paths


class KzenAutoContext private constructor(
    val config: KzenAutoConfig,
    // The process-global extension universe this context runs against: pinned by KzenAutoMain or an embedding
    // host before the first context, or by the first context with the default configuration.
    val runtime: KzenAutoRuntime,
    private val workRootClaim: WorkRootRegistry.Claim
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        init {
            KzenLibCommonModule.register()
            KzenAutoCommonModule.register()
            KzenAutoJvmModule.register()

            // JVM net for @Reflect classes with no generated registration, e.g. non-KSP plugins;
            // generated registrations always win, and every fallback hit is logged
            GlobalMirror.register(ReflectiveClassMirror.global)
        }


        private val logger = LoggerFactory.getLogger(KzenAutoContext::class.java)


        // Construction is self-initializing: callers get a ready context (observers wired,
        // stable ids pre-warmed) rather than having to remember a separate init() step. The work root is
        // claimed first and released again if anything after the claim fails, so a failed creation leaves
        // no claim behind.
        fun create(config: KzenAutoConfig): KzenAutoContext {
            val runtime = KzenAutoRuntime.currentOrDefault()
            val claim = claimWorkRoot(config, runtime)
            try {
                return KzenAutoContext(config, runtime, claim).also { it.init() }
            }
            catch (failure: Throwable) {
                claim.release()
                throw failure
            }
        }


        // Create → toRealPath() → claim, unconditionally canonical: "claim where it exists" would leave an alias
        // race between two contexts creating the same root, and Windows case or symlink aliases defeat string
        // comparison. Claimed before any boot sweep can touch the root.
        private fun claimWorkRoot(config: KzenAutoConfig, runtime: KzenAutoRuntime): WorkRootRegistry.Claim {
            val configured = config.workRoot ?: WorkUtils.standaloneRoot
            Files.createDirectories(configured)
            return runtime.workRoots.claim(configured.toRealPath())
        }


        // Defaulted config for tests that drive in-process logic and don't need a real port/host. Each test
        // context gets its own temporary work root: the standalone default is one root per process, and a
        // test that never closes its context would otherwise hold that root against every later test.
        fun forTest(): KzenAutoContext {
            return create(KzenAutoConfig(
                jsModuleName = "kzen-auto-js",
                workRoot = kotlin.io.path.createTempDirectory("kzen-auto-test-work")))
        }


        // Size budget for the compiled-expression cache; least recently used entries beyond it are
        // evicted (transparently recompiled on next use).
        private const val codeCacheBudgetBytes = 1024L * 1024 * 1024

        // Rolling server logs, cwd-relative (see logback.xml LOG_DIR).
        private val logDir = Paths.get("logs")
    }


    //-----------------------------------------------------------------------------------------------------------------
    val notationMetadataReader = NotationMetadataReader()

    private val fileLocator: FileNotationLocator = GradleLocator(
        moduleRootOverride = config.moduleRoot)
    private val fileMedia = FileNotationMedia(fileLocator)

    // Bundled notation of every plugin scope (the application classpath included), discovered once by the runtime
    // with exact origins; the project's own on-disk documents shadow bundled ones of the same path.
    private val readOnlyMedia: NotationMedia = runBlocking {
        LiteralNotationMedia.filter(runtime.bundledNotation, fileMedia)
    }

    val notationMedia: NotationMedia = ReadWriteNotationMedia(
        fileMedia, readOnlyMedia)

    val targetLocator = TargetLocator(notationMedia)

    private val notationParser: NotationParser = YamlNotationParser()

    val graphDefiner = GraphDefiner
    val graphCreator = GraphCreator
    val notationReducer = NotationReducer(listOf(KzenAutoCodeReferenceRewriter))

    val graphStore = DirectGraphStore(
        notationMedia,
        notationParser,
        notationMetadataReader,
        graphDefiner,
        notationReducer)

    // Process-global stable-id ↔ location mapping. Observes graphStore from boot so
    // identity survives renames across the entire server lifetime — including the gap
    // between a run terminating and the user editing the notation afterward.
    val objectStableMapper = ObjectStableMapper()

    // The work root was created, canonicalized and claimed in create() before anything below sweeps it
    // (JobWorkPool's boot sweep runs in its constructor); the claim is held until close has stopped the run.
    val workUtils = WorkUtils(workRootClaim.realPath, WorkUtils.freshSignature(workRootClaim.token))
    val reportWorkPool = ReportWorkPool(workUtils)
    val jobWorkPool = JobWorkPool(workUtils)

    val kotlinCompiler = ScriptKotlinCompiler()
    val cachedKotlinCompiler = CachedKotlinCompiler(kotlinCompiler, workUtils)
    val kotlinSyntaxValidator = KotlinSyntaxValidator()
    val calculatedColumnEval = CalculatedColumnEval(cachedKotlinCompiler, kotlinSyntaxValidator)
    val jobExpressionCompiler = JobExpressionCompiler(cachedKotlinCompiler, kotlinSyntaxValidator)
    val scriptValidationCache = ScriptValidationCache()
    val jobValidationCache = JobValidationCache()


    // The Report definers are the host's built-ins only: the per-document plugin jar (`jarPath`,
    // `META-INF/kzen/plugins.yaml`) retired in HS21 — plugins contribute readers to Jobs through the runtime's
    // scopes, not definers to Reports. Built before fileListingAction, which depends on it.
    val definitionRepository: ReportDefinitionRepository = HostReportDefinitionRepository(listOf(
        CsvReportDefiner(),
        TsvReportDefiner(),
        TextReportDefiner()))

    val fileListingAction = FileListingAction(definitionRepository)
    val schemaCache = SchemaCache(workUtils)
    val contentProviderLookup = DataContentProviderLookup(LocalDataContentProvider(), emptyMap())
    val sequentialContentStack = SequentialContentStack(contentProviderLookup)
    val sourceFormatResolutionBudgetFactory = SourceFormatResolutionBudgetFactory()
    // Per-context capability instances from the runtime's provider descriptors (the SPI makes no thread-safety demand)
    val readerCapabilityRegistry = ReaderCapabilityRegistry.forRuntime(runtime)
    val configuredDataOpener = ConfiguredDataOpener(
        schemaCache, readerCapabilityRegistry, sequentialContentStack)
    val dataOpenerLookup = DataOpenerLookup(configuredDataOpener)
    val columnListingAction = ColumnListingAction(schemaCache)


    //-----------------------------------------------------------------------------------------------------------------
    // Runtime services exposed to @Service constructor parameters of graph-instantiated objects,
    // keyed by the type each consumer declares. The two members constructed below this point
    // (logicTrace, serverLogicController) are registered as memoized providers, resolved on first
    // use at request/run time - long after construction completes. Nothing may resolve the
    // environment during construction (an eager createGraph here would see them still null).
    val graphEnvironment: GraphEnvironment = GraphEnvironment.builder()
        .put(ClassName(KzenAutoConfig::class.qualifiedName!!), config)
        .put(ClassName(GraphCreator::class.qualifiedName!!), graphCreator)
        .put(ClassName(ObjectStableMapper::class.qualifiedName!!), objectStableMapper)
        .put(ClassName(CachedKotlinCompiler::class.qualifiedName!!), cachedKotlinCompiler)
        .put(ClassName(ScriptValidationCache::class.qualifiedName!!), scriptValidationCache)
        .put(ClassName(JobValidationCache::class.qualifiedName!!), jobValidationCache)
        .put(ClassName(NotationMedia::class.qualifiedName!!), notationMedia)
        .put(ClassName(TargetLocator::class.qualifiedName!!), targetLocator)
        .put(ClassName(NotationMetadataReader::class.qualifiedName!!), notationMetadataReader)
        .put(ClassName(LocalGraphStore::class.qualifiedName!!), graphStore)
        .put(ClassName(ReportWorkPool::class.qualifiedName!!), reportWorkPool)
        .put(ClassName(ReportDefinitionRepository::class.qualifiedName!!), definitionRepository)
        .put(ClassName(CalculatedColumnEval::class.qualifiedName!!), calculatedColumnEval)
        .put(ClassName(JobExpressionCompiler::class.qualifiedName!!), jobExpressionCompiler)
        .put(ClassName(FileListingAction::class.qualifiedName!!), fileListingAction)
        .put(ClassName(ColumnListingAction::class.qualifiedName!!), columnListingAction)
        .put(ClassName(SchemaCache::class.qualifiedName!!), schemaCache)
        .put(ClassName(DataOpenerLookup::class.qualifiedName!!), dataOpenerLookup)
        .put(ClassName(SourceFormatResolutionBudgetFactory::class.qualifiedName!!), sourceFormatResolutionBudgetFactory)
        .put(ClassName(AutomaticFormatResolver::class.qualifiedName!!)) { automaticFormatResolver }
        .put(ClassName(ConfiguredRecordFormatLookup::class.qualifiedName!!)) { configuredRecordFormatRegistry }
        .put(ClassName(ConfiguredRecordFormatRegistry::class.qualifiedName!!)) { configuredRecordFormatRegistry }
        .put(ClassName(GraphInstanceCache::class.qualifiedName!!)) { graphInstanceCache }
        .put(ClassName(LogicTrace::class.qualifiedName!!)) { logicTrace }
        .put(ClassName(ServerLogicController::class.qualifiedName!!)) { serverLogicController }
        .put(ClassName(KzenAutoRuntime::class.qualifiedName!!), runtime)
        .put(ClassName(PluginAvailability::class.qualifiedName!!)) { pluginAvailability }
        // Host services last, so a host key kzen already provides fails here by name rather than shadowing
        .also { builder -> config.hostServices.services.forEach { (name, service) -> builder.put(name, service) } }
        .build()


    // Which plugin-contributed classes this workspace can instantiate, given the services its environment provides
    val pluginAvailability = PluginAvailability(runtime, graphEnvironment)


    //-----------------------------------------------------------------------------------------------------------------
    val serverLogicController = ServerLogicController(
        graphStore, objectStableMapper, cachedKotlinCompiler, scriptValidationCache,
        jobValidationCache, notationMetadataReader, jobWorkPool, graphEnvironment)

    // The trace-query surface (the former LogicTraceStore): projects the controller's retained RunEngine at
    // query time, translating each flavour's within-node emit address to its wire LogicTracePath via the same
    // per-flavour routings. Built after the controller (it reads the controller's active/retained run).
    val logicTrace = RunEngineLogicTrace(
        objectStableMapper,
        listOf(JobTraceAddressRouting, ReportTraceAddressRouting),
        { serverLogicController.retainedTraceAccess() },
        { serverLogicController.clearRetainedTrace() })

    // Scoped, digest-keyed instance reuse for detached actions and tasks.
    val graphInstanceCache = GraphInstanceCache(graphCreator, graphEnvironment)

    val configuredRecordFormatRegistry = ConfiguredRecordFormatRegistry(
        graphStore, graphInstanceCache, readerCapabilityRegistry)

    val automaticFormatResolver = AutomaticFormatResolver(
        configuredRecordFormatRegistry,
        readerCapabilityRegistry,
        DetectionSampleAcquirer(sequentialContentStack))

    val detachedExecutor = ModelDetachedExecutor(
        graphStore, graphInstanceCache)

    val modelTaskRepository = ModelTaskRepository(
        graphStore, graphInstanceCache)

    // Areas are registered (and the code-cache evictor attached) in init(), where the
    // active-run checks can capture serverLogicController without construction-order cycles.
    val managedStorageRegistry = ManagedStorageRegistry()

    val notationQueryHandler = NotationQueryHandler(notationMedia)
    val notationCommandHandler = NotationCommandHandler(graphStore, notationParser)
    val detachedActionHandler = DetachedActionHandler(detachedExecutor, jobWorkPool)
    val taskHandler = TaskHandler(modelTaskRepository)
    val logicHandler = LogicHandler(serverLogicController, logicTrace, graphStore)
    val objectStableHandler = ObjectStableHandler(objectStableMapper)
    val fileListingHandler = FileListingHandler(fileListingAction)
    val storageHandler = StorageHandler(managedStorageRegistry)


    //-----------------------------------------------------------------------------------------------------------------
    private fun init() {
        runBlocking {
            graphStore.observe(modelTaskRepository)
            graphStore.observe(objectStableMapper)
            graphStore.observe(serverLogicController)
            graphStore.observe(pluginAvailability)
            pluginAvailability.learn(graphStore.graphNotation())

            // Pre-warm so stable ids reflect names-at-boot deterministically, independent
            // of first-access order during run execution. Notation-level enumeration so
            // a partially-broken graph (definition errors) still pre-warms.
            for (location in graphStore.graphNotation().objectLocations) {
                objectStableMapper.objectStableId(location)
            }
        }

        initManagedStorage()

        // Fail fast (with names) on any @Service parameter type the environment doesn't provide, rather than
        // at graph-creation time. Runs after every module register() call (they happen in the companion init).
        ServiceEnvironmentValidation.validate(graphEnvironment)
    }


    private fun initManagedStorage() {
        val anyRunActive = { serverLogicController.status().active != null }

        val codeCacheArea = cachedKotlinCompiler.storageArea(codeCacheBudgetBytes)
        val codeCacheEvictor = StorageLruEvictor(codeCacheArea)
        cachedKotlinCompiler.attachEvictor(codeCacheEvictor)

        managedStorageRegistry.register(codeCacheArea)

        managedStorageRegistry.register(ReportStorageArea(
            workUtils.resolve(ReportWorkPool.defaultReportDir), reportWorkPool))

        managedStorageRegistry.register(SchemaCacheStorageArea(
            workUtils.resolve(SchemaCache.indexDirName), anyRunActive, schemaCache::invalidate))

        managedStorageRegistry.register(JobOutputStorageArea(
            jobWorkPool.workerOutputBase(),
            { runBlocking { graphStore.graphNotation() } },
            anyRunActive))

        managedStorageRegistry.register(DirectoryStorageArea(
            "job-scratch",
            "Job scratch",
            "Transient per-run compute state of Job workers; cleaned up automatically when the run " +
                "settles and at server start.",
            jobWorkPool.scratchBase(),
            deletable = false))

        if (config.manageLogs) {
            managedStorageRegistry.register(DirectoryStorageArea(
                "logs",
                "Logs",
                "Rolling server logs; old archives are pruned automatically.",
                logDir,
                deletable = false))
        }

        // Reclaims budget overshoot accumulated while no evictor was attached (prior process).
        codeCacheEvictor.maybeEvict()
    }


    /**
     * Shutdown order for any owner: stop and await the HTTP server first (the server is created outside the
     * context by `ktorMain`'s caller), then this — which cancels the active run, joins its executor, and only
     * then releases the work-root claim. If the run cannot be joined the claim is kept: the root is not
     * advertised as reusable while a Worker could still be writing to it. Idempotent.
     */
    override fun close() {
        // Cancelling the active run settles its root node, which disposes the run-scoped resources (a browser
        // opened with closePolicy Auto/KeepOnFailure) via the engine.
        val joined = serverLogicController.closeAndJoin()
        if (joined) {
            workRootClaim.release()
        }
        else {
            logger.error("Active execution did not join during close; work root {} stays claimed", workUtils.base())
        }
    }


    /** Whether close released this context's work root (false while unclosed, or if the run could not be joined). */
    fun isWorkRootReleased(): Boolean {
        return workRootClaim.isReleased
    }
}
