package tech.kzen.auto.server.util

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.codegen.KzenAutoCommonModule
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.server.codegen.KzenAutoJvmModule
import tech.kzen.lib.common.codegen.KzenLibCommonModule
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.media.LiteralNotationMedia
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.service.media.ReadWriteNotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.collect.toPersistentMap
import tech.kzen.lib.server.notation.ClasspathNotationMedia
import tech.kzen.lib.server.notation.FileNotationMedia
import tech.kzen.lib.server.notation.locate.GradleLocator


object AutoTestUtils {
    //-----------------------------------------------------------------------------------------------------------------
    init {
        KzenLibCommonModule.register()
        KzenAutoCommonModule.register()
        KzenAutoJvmModule.register()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun readNotation(): GraphNotation {
        val locator = GradleLocator(true)
        val notationMedia = FileNotationMedia(locator)

        val classpathNotationMedia = ClasspathNotationMedia(
            exclude = listOf(AutoConventions.autoMainDocumentNesting))

        val notationParser: NotationParser = YamlNotationParser()

        return runBlocking {
            val notationProjectBuilder =
                mutableMapOf<DocumentPath, DocumentNotation>()

            val readOnlyMedia: NotationMedia =
                LiteralNotationMedia.filter(classpathNotationMedia, notationMedia)

            val combinedNotationMedia = ReadWriteNotationMedia(
                notationMedia, readOnlyMedia)

            for (notationPath in combinedNotationMedia.scan().documents.map) {
                val notationModule = combinedNotationMedia.readDocument(notationPath.key)
                val objects = notationParser.parseDocumentObjects(notationModule)
                notationProjectBuilder[notationPath.key] = DocumentNotation(
                    objects,
                    null)
            }

            GraphNotation(
                DocumentPathMap(
                    notationProjectBuilder.toPersistentMap())
            )
        }
    }


    fun graphDefinitionAttempt(graphNotation: GraphNotation): GraphDefinitionAttempt {
        val graphMetadata = graphMetadata(graphNotation)
        val graphStructure = GraphStructure(graphNotation, graphMetadata)
        return GraphDefiner().tryDefine(graphStructure)
    }


    fun graphMetadata(graphNotation: GraphNotation): GraphMetadata {
        val notationMetadataReader = NotationMetadataReader()
        return notationMetadataReader.read(graphNotation)
    }
}