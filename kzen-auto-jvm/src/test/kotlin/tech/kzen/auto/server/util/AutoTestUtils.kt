package tech.kzen.auto.server.util

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.codegen.KzenAutoCommonModule
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.server.codegen.KzenAutoJvmModule
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.lib.common.codegen.KzenLibCommonModule
import tech.kzen.lib.common.exec.logic.Logic
import tech.kzen.lib.common.exec.logic.LogicExecution
import tech.kzen.lib.common.exec.logic.LogicHandle
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.location.ObjectLocation
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
import tech.kzen.lib.server.exec.logic.context.MutableLogicControl
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
        return GraphDefiner.tryDefine(graphStructure)
    }


    fun graphMetadata(graphNotation: GraphNotation): GraphMetadata {
        val notationMetadataReader = NotationMetadataReader()
        return notationMetadataReader.read(graphNotation)
    }


    /**
     * Instantiate a logic document (e.g. a ScriptDocument) from notation through the real graph and
     * open its live LogicExecution — exercising the production Logic.execute path with @Service
     * dependencies resolved from the context's environment, rather than hand-constructing the
     * execution. The returned execution is driven by the caller via continueOrStart(control, ...).
     */
    fun liveLogicExecution(
        context: KzenAutoContext,
        logicLocation: ObjectLocation,
        runExecutionId: LogicRunExecutionId,
        logicHandle: LogicHandle
    ): LogicExecution {
        val graphDefinition = graphDefinitionAttempt(readNotation())
            .transitiveSuccessful
            .filterTransitive(logicLocation.documentPath)

        val graphInstance = context.graphCreator.createGraph(
            graphDefinition, context.graphEnvironment)

        val logic = graphInstance.objectInstances[logicLocation]?.reference as? Logic
            ?: throw IllegalArgumentException("Logic not found: $logicLocation")

        val logicTraceHandle = context.logicTraceStore.handle(runExecutionId, logicLocation)

        return logic.execute(
            logicHandle,
            logicTraceHandle,
            runExecutionId,
            MutableLogicControl(false))
    }
}