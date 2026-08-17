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
import tech.kzen.lib.common.reflect.GlobalMirror
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
import tech.kzen.lib.server.reflect.ReflectiveClassMirror


object AutoTestUtils {
    //-----------------------------------------------------------------------------------------------------------------
    init {
        KzenLibCommonModule.register()
        KzenAutoCommonModule.register()
        KzenAutoJvmModule.register()

        // Test fixtures reach the graph through this bootstrap as well as through KzenAutoContext,
        // so the fallback has to be registered on both paths
        GlobalMirror.register(ReflectiveClassMirror.global)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The on-disk notation corpus is fixture data: read-only for the life of a test run, but reading it parses
    // every document in the project, and the suite asks for it at over a hundred call sites. Parsed once and
    // shared — GraphNotation is immutable (persistent maps), so no test can hand a mutation of it to the next.
    // No test writes the corpus either; the ones that write notation use an in-memory MapNotationMedia.
    //
    // Deliberately NOT extended to graphDefinitionAttempt: an AttributeDefiner may embed a live object in the
    // definition it returns (FlowWiring mints MutableRequiredInput / MutableFlowOutput channels), so definitions
    // are not shareable across tests the way notation and metadata are.
    private val sharedNotation: GraphNotation by lazy {
        parseNotation()
    }

    private val sharedMetadata: GraphMetadata by lazy {
        NotationMetadataReader().read(sharedNotation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun readNotation(): GraphNotation {
        return sharedNotation
    }


    private fun parseNotation(): GraphNotation {
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
                // folders are bare directories with no document body — skip them (mirrors SeededNotationMedia),
                // otherwise readDocument tries to read a directory and fails
                if (notationPath.key.folder) {
                    continue
                }
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
        return GraphDefiner.tryDefine(graphStructure)
    }


    fun graphMetadata(graphNotation: GraphNotation): GraphMetadata {
        return when {
            graphNotation === sharedNotation -> sharedMetadata
            else -> NotationMetadataReader().read(graphNotation)
        }
    }
}