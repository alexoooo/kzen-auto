package tech.kzen.auto.client

import react.dom.render
import tech.kzen.auto.client.objects.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.CommandBus
//import tech.kzen.auto.client.service.CommandObserver
import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.context.ObjectGraphCreator
import tech.kzen.lib.common.context.ObjectGraphDefiner
import kotlin.browser.document
import kotlin.browser.window


fun main(args: Array<String>) {
    ClientContext.init()

    window.onload = {
        async {
            ClientContext.modelManager.refresh()

            val autoNotation = ClientContext.modelManager.autoNotation()
            val autoMetadata = ClientContext.notationMetadataReader.read(autoNotation)
            val graphDefinition = ObjectGraphDefiner.define(autoNotation, autoMetadata)
            val autoGraph = ObjectGraphCreator.createGraph(graphDefinition, autoMetadata)

//            console.log("^^^ main autoGraph", autoGraph)

            val rootInstance = autoGraph.get("root")
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
