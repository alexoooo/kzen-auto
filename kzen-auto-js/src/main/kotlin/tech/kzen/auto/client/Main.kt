package tech.kzen.auto.client

import browser.document
import browser.window
import react.dom.render
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.locate.ObjectReference


fun main() {
    ClientContext.init()

    window.onload = {
        async {
            ClientContext.initAsync()

            val clientGraphDefinition = ClientContext.mirroredGraphStore
                    .graphDefinition()
                    .successful()
                    .filterDefinitions(AutoConventions.clientUiAllowed)
//            console.log("^^^ filteredGraphDefinition - $clientGraphDefinition")

            val clientGraphInstance = ClientContext.graphCreator
                    .createGraph(clientGraphDefinition)

//            console.log("^^^ main autoGraph ^^ ", autoGraph.objects.values.keys.toString())
            val rootLocation = clientGraphInstance
                    .objectInstances
                    .locate(ObjectReference.parse("root"))

            val rootInstance = clientGraphInstance
                    .objectInstances[rootLocation]
                    ?.reference
                    as? ReactWrapper<*>
                    ?: throw IllegalStateException("Missing root object")

            val rootElement = document.getElementById("root")
                    ?: throw IllegalStateException("'root' element not found")
            document.createElement("div")

            //rootElement.clear()
            while (rootElement.hasChildNodes()) {
                rootElement.removeChild(rootElement.firstChild!!)
            }

            // TODO: migrate to new version
//            val root = createRoot(rootElement)
//            root.render(<App tab="home" />);

            render(rootElement) {
                rootInstance.child(this) {}
            }
        }
    }
}
