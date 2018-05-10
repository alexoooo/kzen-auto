package tech.kzen.auto.client

import kotlinx.html.dom.create
import kotlinx.html.js.div
import kotlinx.html.p
import org.w3c.dom.get
import react.dom.div
import react.dom.render
import tech.kzen.auto.client.objects.ReactWrapper
//import tech.kzen.auto.client.objects.ReactWrapper
import tech.kzen.lib.client.notation.RestNotationScanner
import tech.kzen.lib.client.notation.RestNotationSource
import tech.kzen.lib.common.context.ObjectGraphCreator
import tech.kzen.lib.common.context.ObjectGraphDefiner
import tech.kzen.lib.common.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.notation.model.PackageNotation
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.read.NotationReader
import tech.kzen.lib.common.notation.read.flat.FlatNotationReader
import tech.kzen.lib.common.notation.read.flat.parser.NotationParser
import tech.kzen.lib.common.notation.read.flat.source.NotationSource
import tech.kzen.lib.common.notation.read.yaml.YamlNotationParser
import tech.kzen.lib.common.notation.scan.NotationScanner
import tech.kzen.lib.platform.ModuleRegistry
import kotlin.browser.document
import kotlin.browser.window
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.EmptyCoroutineContext
import kotlin.coroutines.experimental.startCoroutine
import kotlin.js.Promise


fun main(args: Array<String>) {
    val kzenAutoJs = js("require('kzen-auto-js.js')")
    console.log("kzenAutoJs", kzenAutoJs)
    ModuleRegistry.add(kzenAutoJs)

    console.log("kzen-lib-js", js("require('kzen-lib-js.js')"))

//    val kzenLibCommon = js("require('kzen-lib-common.js')")
//    console.log("kzenLibCommon", kzenLibCommon)
//    ModuleRegistry.add(kzenLibCommon)

    val notationSource: NotationSource = RestNotationSource(".")
    val notationParser: NotationParser = YamlNotationParser()

    val notationReader: NotationReader = FlatNotationReader(
            notationSource, notationParser)

    val notationScanner: NotationScanner = RestNotationScanner(".")

    val notationMetadataReader = NotationMetadataReader()


    window.onload = {
        async {
            val notationProjectBuilder = mutableMapOf<ProjectPath, PackageNotation>()
            for (notationPath in notationScanner.scan()) {
                val notationModule = notationReader.read(notationPath)
                notationProjectBuilder[notationPath] = notationModule
            }
            val notationProject = ProjectNotation(notationProjectBuilder)
            val graphMetadata = notationMetadataReader.read(notationProject)

            val graphDefinition = ObjectGraphDefiner.define(
                    notationProject, graphMetadata)

            val objectGraph = ObjectGraphCreator
                    .createGraph(graphDefinition, graphMetadata)

            val rootInstance = objectGraph.get("root") as ReactWrapper

            render(document.getElementById("root")!!) {
                div {
                    rootInstance.execute(this)
//                    +"foo"
                }
            }
        }
    }
}


// TODO: what does this really do?
fun <T> async(x: suspend () -> T): Promise<T> {
    return Promise { resolve, reject ->
        x.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resume(value: T) {
                resolve(value)
            }

            override fun resumeWithException(exception: Throwable) {
                reject(exception)
            }
        })
    }
}