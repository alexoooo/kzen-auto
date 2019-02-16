package tech.kzen.auto.client

//import tech.kzen.auto.client.service.CommandObserver
import react.dom.render
import tech.kzen.auto.client.objects.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectPath
import tech.kzen.lib.common.context.GraphCreator
import tech.kzen.lib.common.context.GraphDefiner
import tech.kzen.lib.common.structure.GraphStructure
import kotlin.browser.document
import kotlin.browser.window
import kotlin.dom.clear


fun main(args: Array<String>) {
    ClientContext.init()

    window.onload = {
        async {
            ClientContext.modelManager.refresh()

            val autoNotation = ClientContext.modelManager.autoNotation()
            val autoMetadata = ClientContext.notationMetadataReader.read(autoNotation)
            val autoStructure = GraphStructure(autoNotation, autoMetadata)
            val graphDefinition = GraphDefiner.define(autoStructure)
            val autoGraph = GraphCreator.createGraph(autoStructure, graphDefinition)

//            console.log("^^^ main autoGraph ^^ ", autoGraph.objects.values.keys.toString())
            val rootLocation = ObjectLocation(
                    BundlePath.parse("auto/auto-framework.yaml"),
                    ObjectPath.parse("root"))

            val rootInstance = autoGraph.objects.get(rootLocation)
                    as? ReactWrapper
                    ?: throw IllegalStateException("Missing root object")

//            console.log("^^^ main rootInstance", rootInstance)

            val rootElement = document.getElementById("root")
                    ?: throw IllegalStateException("'root' element not found")

            rootElement.clear()

            render(rootElement) {
                rootInstance.execute(this)
            }
        }
    }
}
