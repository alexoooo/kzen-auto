package tech.kzen.auto.client.objects

import react.*
import react.dom.div


@Suppress("unused")
class ContainerComponent : RComponent<ContainerComponent.Props, RState>() {
    override fun RBuilder.render() {
        div {
            for (child in props.children) {
                div(classes = "child") {
                    child.execute(this)
                }
            }
        }
    }

    class Props(
            var children: List<ReactWrapper>
    ) : RProps

    @Suppress("unused")
    class Wrapper(
            private val children: List<ReactWrapper>
    ) : ReactWrapper {
        override fun execute(input: RBuilder): ReactElement {
            return input.child(ContainerComponent::class) {
                //                attrs.childA = childA
//                attrs.childB = childB
                attrs.children = children
            }
        }
    }
}
