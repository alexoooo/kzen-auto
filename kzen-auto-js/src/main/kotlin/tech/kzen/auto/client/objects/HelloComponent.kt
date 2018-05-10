package tech.kzen.auto.client.objects

import react.*
import react.dom.div


@Suppress("unused")
class HelloComponent : RComponent<HelloComponent.Props, RState>() {
    override fun RBuilder.render() {
        div {
            +("Hello: ${props.name}")
        }
    }

    class Props(var name: String) : RProps

    @Suppress("unused")
    class Wrapper(val name: String) : ReactWrapper {
        override fun execute(input: RBuilder): ReactElement {
            return input.child(HelloComponent::class) {
                attrs.name = name
            }
        }
    }
}
