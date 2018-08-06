package tech.kzen.auto.client.service

import tech.kzen.lib.common.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.notation.format.YamlNotationParser
import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.notation.io.NotationParser
import tech.kzen.lib.common.notation.io.common.MapNotationMedia
import tech.kzen.lib.common.notation.repo.NotationRepository
import tech.kzen.lib.platform.ModuleRegistry


object AutoContext {
    //-----------------------------------------------------------------------------------------------------------------
    val restClient = RestClient("")

    val restNotationMedia: NotationMedia = RestNotationMedia(restClient)
    val clientNotationMedia = MapNotationMedia()

    val notationParser: NotationParser = YamlNotationParser()

    val notationMetadataReader = NotationMetadataReader()

    val clientRepository = NotationRepository(
            clientNotationMedia, notationParser)

    val modelManager = ModelManager(
            clientNotationMedia,
            clientRepository,
            restNotationMedia,
            notationMetadataReader)

    val commandBus = CommandBus(
            clientRepository,
            modelManager,
            restClient,
            notationParser)


    //-----------------------------------------------------------------------------------------------------------------
    fun init() {
        val kzenAutoJs = js("require('kzen-auto-js.js')")
//        console.log("kzenAutoJs", kzenAutoJs)
        ModuleRegistry.add(kzenAutoJs)
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun foo() {
//
//        val notation = context.repository.notation()
//        val autoNotation = NotationConventions.autoNotation(notation)
//
//        val autoMetadata = context.notationMetadataReader.read(autoNotation)
//
//        val graphDefinition = ObjectGraphDefiner.define(
//                autoNotation, autoMetadata)
//
//        val autoGraph = ObjectGraphCreator
//                .createGraph(graphDefinition, autoMetadata)
//    }
}