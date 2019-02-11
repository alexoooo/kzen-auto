package tech.kzen.auto.server.service

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.service.ExecutionManager
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.auto.server.notation.BootNotationMedia
import tech.kzen.auto.server.service.webdriver.WebDriverContext
import tech.kzen.auto.server.service.webdriver.WebDriverInstaller
import tech.kzen.auto.server.service.webdriver.WebDriverOptionDao
import tech.kzen.lib.common.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.notation.format.YamlNotationParser
import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.notation.io.common.MapNotationMedia
import tech.kzen.lib.common.notation.io.common.MultiNotationMedia
import tech.kzen.lib.common.notation.repo.NotationRepository
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

    val repository = NotationRepository(
            notationMedia,
            yamlParser,
            notationMetadataReader)


    val modelManager = ModelManager(
            notationMediaCache,
            repository,
            notationMedia,
            notationMetadataReader)

    val actionExecutor = ModelActionExecutor(modelManager)

    val executionManager = ExecutionManager(
            EmptyExecutionInitializer,
            actionExecutor,
            modelManager)


    //-----------------------------------------------------------------------------------------------------------------
    private val downloadManager = DownloadManager()

    val webDriverRepo = WebDriverOptionDao()
    val webDriverInstaller = WebDriverInstaller(downloadManager)
    val webDriverContext = WebDriverContext()



    //-----------------------------------------------------------------------------------------------------------------
    init {
        downloadManager.initialize()

        runBlocking {
            modelManager.observe(executionManager)
        }
    }
}