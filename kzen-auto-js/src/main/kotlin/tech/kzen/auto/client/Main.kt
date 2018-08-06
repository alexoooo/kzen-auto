package tech.kzen.auto.client

import react.dom.render
import tech.kzen.auto.client.objects.ReactWrapper
import tech.kzen.auto.client.service.AutoContext
import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.context.ObjectGraphCreator
import tech.kzen.lib.common.context.ObjectGraphDefiner
//import tech.kzen.auto.client.objects.ReactWrapper
//import tech.kzen.lib.client.notation.RestNotationSource
import kotlin.browser.document
import kotlin.browser.window


fun main(args: Array<String>) {
    val context = AutoContext

    context.init()

    window.onload = {
        async {
            context.modelManager.refresh()

            val autoNotation = context.modelManager.autoNotation()
            val autoMetadata = context.notationMetadataReader.read(autoNotation)
            val graphDefinition = ObjectGraphDefiner.define(autoNotation, autoMetadata)
            val autoGraph = ObjectGraphCreator.createGraph(graphDefinition, autoMetadata)

            val rootInstance = autoGraph.get("root") as ReactWrapper

            render(document.getElementById("root")!!) {
                rootInstance.execute(this)
            }
        }
    }
}
