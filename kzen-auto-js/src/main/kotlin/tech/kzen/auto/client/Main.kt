package tech.kzen.auto.client

import react.dom.render
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.service.GraphStructureManager
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.locate.ObjectLocationMap
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.platform.collect.toPersistentMap
import kotlin.browser.document
import kotlin.browser.window
import kotlin.dom.clear


fun main() {
    ClientContext.init()

    window.onload = {
        async {
            ClientContext.modelManager.refresh()

            val clientGraphStructure = ClientContext.modelManager.clientGraphStructure()
            val clientGraphDefinition = ClientContext.graphDefiner.define(clientGraphStructure)

//            val filteredGraphStructure = clientGraphStructure.

            val filteredGraphDefinition = clientGraphDefinition
                    .objectDefinitions
                    .values
                    .filterKeys { ! it.documentPath.startsWith(GraphStructureManager.autoJvmPrefixDocumentNesting) }
                    .toPersistentMap()
                    .let { GraphDefinition(ObjectLocationMap(it), clientGraphStructure) }

//            console.log("^^^ filteredGraphDefinition - $filteredGraphDefinition")

            val clientGraphInstance = ClientContext.graphCreator.createGraph(
                    clientGraphStructure, filteredGraphDefinition)

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

            rootElement.clear()

            render(rootElement) {
                rootInstance.child(this) {}
            }
        }
    }
}
