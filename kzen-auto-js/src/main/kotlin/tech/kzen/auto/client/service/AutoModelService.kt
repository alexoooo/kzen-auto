package tech.kzen.auto.client.service

import tech.kzen.auto.client.rest.RestNotationMedia
import tech.kzen.auto.client.rest.RestNotationScanner
import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.context.ObjectGraphCreator
import tech.kzen.lib.common.context.ObjectGraphDefiner
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.notation.format.YamlNotationParser
import tech.kzen.lib.common.notation.io.NotationIo
import tech.kzen.lib.common.notation.io.flat.FlatNotationIo
import tech.kzen.lib.common.notation.io.flat.media.NotationMedia
import tech.kzen.lib.common.notation.io.flat.parser.NotationParser
import tech.kzen.lib.common.notation.model.PackageNotation
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.scan.NotationScanner
import tech.kzen.lib.platform.ModuleRegistry


object AutoModelService {
    //-----------------------------------------------------------------------------------------------------------------
    val notationMedia: NotationMedia = RestNotationMedia(".")
    val notationParser: NotationParser = YamlNotationParser()

    val notationIo: NotationIo = FlatNotationIo(
            notationMedia, notationParser)

    val notationScanner: NotationScanner = RestNotationScanner(".")

    val notationMetadataReader = NotationMetadataReader()


    //-----------------------------------------------------------------------------------------------------------------
    fun init() {
        val kzenAutoJs = js("require('kzen-auto-js.js')")
//        console.log("kzenAutoJs", kzenAutoJs)
        ModuleRegistry.add(kzenAutoJs)
    }



    //-----------------------------------------------------------------------------------------------------------------
    suspend fun autoGraph(): ObjectGraph {
        val autoNotation = autoNotation()
        return graph(autoNotation)
    }


    private suspend fun autoNotation(): ProjectNotation {
        val projectNotation = allNotation()

        return projectNotation.filterPaths {
            it.relativeLocation.startsWith("notation/base/") ||
            it.relativeLocation.startsWith("notation/auto/")
        }
    }


    suspend fun projectNotation(): ProjectNotation {
        val projectNotation = allNotation()

        println("!! All packages: ${projectNotation.packages.keys}")

        return projectNotation.filterPaths {
            it.relativeLocation.startsWith("notation/base/") ||
            (! it.relativeLocation.startsWith("notation/auto/") ||
                    it.relativeLocation.endsWith("auto-common.yaml"))
        }
    }


    private suspend fun allNotation(): ProjectNotation {
        val notationProjectBuilder = mutableMapOf<ProjectPath, PackageNotation>()
        for (notationPath in notationScanner.scan()) {
            val notationModule = notationIo.read(notationPath)
            notationProjectBuilder[notationPath] = notationModule
        }
        return ProjectNotation(notationProjectBuilder)
    }



    //-----------------------------------------------------------------------------------------------------------------
    fun metadata(projectNotation: ProjectNotation): GraphMetadata {
        return notationMetadataReader.read(projectNotation)
    }


    fun graph(
            projectNotation: ProjectNotation,
            graphMetadata: GraphMetadata = metadata(projectNotation)
    ): ObjectGraph {
        val graphDefinition = ObjectGraphDefiner.define(
                projectNotation, graphMetadata)

        return ObjectGraphCreator
                .createGraph(graphDefinition, graphMetadata)
    }
}