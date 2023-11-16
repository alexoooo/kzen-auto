package tech.kzen.auto.client

import react.Fragment
import react.StrictMode
import react.create
import react.dom.client.createRoot
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.api.rootHtmlElementId
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectReference
import web.dom.document
import web.html.HTMLElement
import web.window.window


fun main() {
//    console.log("^^^ main!!")
    ClientContext.init()

    fun emptyRootElement(): HTMLElement {
        val rootElement = document.getElementById(rootHtmlElementId)
            ?: throw IllegalStateException("'$rootHtmlElementId' element not found")

        while (rootElement.hasChildNodes()) {
            rootElement.removeChild(rootElement.firstChild!!)
        }
        return rootElement
    }

    window.onload = {
        async {
            ClientContext.initAsync()

            val clientGraphDefinition = ClientContext.mirroredGraphStore
                    .graphDefinition()
                    .successful()
                    .filterDefinitions(AutoConventions.clientUiAllowed)
//            console.log("^^^ filteredGraphDefinition - $clientGraphDefinition")

            val clientGraphInstance: GraphInstance =
                try {
                    ClientContext.graphCreator
                        .createGraph(clientGraphDefinition)
                }
                catch (t: Throwable) {
                    val rootElement = emptyRootElement()
                    rootElement.textContent = "Error: ${t.message}"
                    throw t
                }

//            console.log("^^^ main autoGraph ^^ ", autoGraph.objects.values.keys.toString())
            val rootLocation = clientGraphInstance
                    .objectInstances
                    .locate(ObjectReference.parse("root"))

            val rootInstance = clientGraphInstance
                    .objectInstances[rootLocation]
                    ?.reference
                    as? ReactWrapper<*>
                    ?: throw IllegalStateException("Missing root object")

            val rootElement = emptyRootElement()

            createRoot(rootElement).render(Fragment.create {
                StrictMode {
                    rootInstance.child(this) {}
                }
            })
        }
    }
}
