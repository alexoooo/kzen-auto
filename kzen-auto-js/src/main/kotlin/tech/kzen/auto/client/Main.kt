package tech.kzen.auto.client

import csstype.px
import csstype.rgb
import emotion.react.css
import react.*
import react.dom.client.createRoot
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.common.api.rootHtmlElementId
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectReference
import web.dom.document
import web.html.HTMLElement
import web.html.InputType
import web.window.window


fun main() {
    console.log("^^^ main!!")
    ClientContext.init()

    fun emptyRootElement(): HTMLElement {
        val rootElement = document.getElementById(rootHtmlElementId)
            ?: throw IllegalStateException("'root' element not found")

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
                    rootElement.textContent = "Error: $t"
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

            // TODO: migrate to new version
//            val root = createRoot(rootElement)
//            root.render(<App tab="home" />);

//            render(rootElement) {
//                rootInstance.child(this) {}
//            }
//            createRoot(rootElement).render(welcome)

            val welcome = Welcome.create {
                name = "Kzen"
                rootInstance.child(this) {}
            }

            createRoot(rootElement).render(welcome)
        }
    }
}


external interface WelcomeProps : Props {
    var name: String
}


val Welcome = FC<WelcomeProps> { props ->
    var name by useState(props.name)

    div {
        css {
            padding = 5.px
            backgroundColor = rgb(8, 97, 22)
            color = rgb(56, 246, 137)
        }
        +"Hello, $name!"
        br {}

        TestComponent::class.react {
            this.name = name
        }
        br {}
        TestComponent.Wrapper().child(this) {
            this.name = name
        }
    }
    input {
        css {
            marginTop = 5.px
            marginBottom = 5.px
            fontSize = 14.px
        }
        type = InputType.text
        value = name
        onChange = { event ->
            name = event.target.value
        }
    }
}


class TestComponent : RComponent<WelcomeProps, State>() {
    class Wrapper: ReactWrapper<WelcomeProps> {
        override fun ChildrenBuilder.child(block: WelcomeProps.() -> Unit) {
            TestComponent::class.react {
                block()
            }
        }
    }


    override fun ChildrenBuilder.render() {
        div {
            +"Hello, world! - ${props.name}"
        }
    }
}