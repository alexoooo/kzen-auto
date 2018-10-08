package tech.kzen.auto.server.service

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
    val notationMediaCache = MapNotationMedia()
    val notationMetadataReader = NotationMetadataReader()


    val fileLocator = GradleLocator()
    val fileMedia = FileNotationMedia(fileLocator)

    val bootMedia = BootNotationMedia()

    val notationMedia: NotationMedia = MultiNotationMedia(listOf(
            fileMedia, bootMedia))

    val yamlParser = YamlNotationParser()

    val repository = NotationRepository(
            notationMedia,
            yamlParser)


    val modelManager = ModelManager(
            notationMediaCache,
            repository,
            notationMedia,
            notationMetadataReader)


    //-----------------------------------------------------------------------------------------------------------------
    val webDriverRepo = WebDriverOptionDao()
    val webDriverInstaller = WebDriverInstaller()
    val webDriverContext = WebDriverContext()
}