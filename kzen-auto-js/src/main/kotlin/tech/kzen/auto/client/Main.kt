package tech.kzen.auto.client

import react.dom.render
import tech.kzen.auto.client.service.AutoModelService
import tech.kzen.auto.client.objects.ReactWrapper
import tech.kzen.auto.client.util.async
//import tech.kzen.auto.client.objects.ReactWrapper
//import tech.kzen.lib.client.notation.RestNotationSource
import kotlin.browser.document
import kotlin.browser.window


fun main(args: Array<String>) {
    AutoModelService.init()

//    val kzenAutoJs = js("require('kzen-auto-js.js')")
//    console.log("kzenAutoJs", kzenAutoJs)
//    ModuleRegistry.add(kzenAutoJs)
//
//    console.log("kzen-lib-js", js("require('kzen-lib-js.js')"))

//    val notationSource: NotationMedia = RestNotationMedia(".")
//    val notationParser: NotationParser = YamlNotationParser()
//
//    val notationReader: NotationIo = FlatNotationIo(
//            notationSource, notationParser)
//
//    val notationScanner: NotationScanner = RestNotationScanner(".")
//
//    val notationMetadataReader = NotationMetadataReader()


    window.onload = {
        async {
            val autoGraph = AutoModelService.autoGraph()

            println("!!! GOT GRAPH")

            val rootInstance = autoGraph.get("root") as ReactWrapper

            render(document.getElementById("root")!!) {
                rootInstance.execute(this)
            }
        }
    }
}
