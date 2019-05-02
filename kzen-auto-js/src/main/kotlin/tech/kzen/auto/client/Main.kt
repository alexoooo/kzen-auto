package tech.kzen.auto.client

import react.dom.render
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.context.GraphCreator
import tech.kzen.lib.common.context.GraphDefiner
import tech.kzen.lib.common.model.locate.ObjectReference
import kotlin.browser.document
import kotlin.browser.window
import kotlin.dom.clear


fun main() {
    ClientContext.init()

    window.onload = {
        async {
            ClientContext.modelManager.refresh()

            val clientGraphStructure = ClientContext.modelManager.clientGraphStructure()
            val clientGraphDefinition = GraphDefiner.define(clientGraphStructure)
            val clientGraphInstance = GraphCreator.createGraph(clientGraphStructure, clientGraphDefinition)

//            console.log("^^^ main autoGraph ^^ ", autoGraph.objects.values.keys.toString())
            val rootLocation = clientGraphInstance.objects
                    .locate(ObjectReference.parse("root"))

            val rootInstance = clientGraphInstance.objects.get(rootLocation)
                    as? ReactWrapper<*>
                    ?: throw IllegalStateException("Missing root object")

//            console.log("^^^ main rootInstance", rootInstance)

            val rootElement = document.getElementById("root")
                    ?: throw IllegalStateException("'root' element not found")

            rootElement.clear()

            render(rootElement) {
                rootInstance.child(this) {}
            }
        }
    }
}
