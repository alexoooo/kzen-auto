package tech.kzen.auto.client

import react.dom.render
import tech.kzen.auto.client.objects.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.CommandBus
//import tech.kzen.auto.client.service.CommandObserver
import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectPath
import tech.kzen.lib.common.context.GraphCreator
import tech.kzen.lib.common.context.GraphDefiner
import kotlin.browser.document
import kotlin.browser.window


fun main(args: Array<String>) {
    ClientContext.init()

    window.onload = {
        async {
            ClientContext.modelManager.refresh()

            val autoNotation = ClientContext.modelManager.autoNotation()
            val autoMetadata = ClientContext.notationMetadataReader.read(autoNotation)
            val graphDefinition = GraphDefiner.define(autoNotation, autoMetadata)
            val autoGraph = GraphCreator.createGraph(graphDefinition, autoMetadata)

//            console.log("^^^ main autoGraph", autoGraph)
            val rootLocation = ObjectLocation(
                    BundlePath.parse("auto/auto-framework.yaml"),
                    ObjectPath.parse("root"))

            val rootInstance = autoGraph.objects.get(rootLocation)
                    as? ReactWrapper
                    ?: throw IllegalStateException("Missing root object")

            console.log("^^^ main rootInstance", rootInstance)

//            val commandObserverName = autoGraph
//                    .names()
//                    .find { autoGraph.get(it) is CommandBus.Observer }
//            if (commandObserverName != null) {
//                // TODO: AutoProject (root) should be here?
//                console.log("%%%%% main - commandObserverName: $commandObserverName")
//                val commandObserver = autoGraph.get(commandObserverName) as CommandBus.Observer
//                ClientContext.commandBus.setObserver(commandObserver)
//            }

            console.log("^^^ main autoGraph", autoGraph)

            render(document.getElementById("root")!!) {
                rootInstance.execute(this)
            }
        }
    }
}
