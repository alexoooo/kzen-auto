//package tech.kzen.auto.client.objects
//
//import kotlinx.html.InputType
//import kotlinx.html.js.onChangeFunction
//import kotlinx.html.js.onClickFunction
//import org.w3c.dom.HTMLInputElement
//import react.*
//import react.dom.div
//import react.dom.input
//import tech.kzen.auto.client.async
//import tech.kzen.lib.client.command.RestCommandApi
//
//
//@Suppress("unused")
//class HelloComponent(
//        props: HelloComponent.Props
//) : RComponent<HelloComponent.Props, HelloComponent.State>(props) {
//
//    //-----------------------------------------------------------------------------------------------------------------
//    class Props(
//            var objectName: String,
//            var name: String
//    ) : RProps
//
//
//    class State(
//            var name: String
//    ) : RState
//
//
//    @Suppress("unused")
//    class Wrapper(
//            val objectName: String,
//            val name: String
//    ) : ReactWrapper {
//        override fun execute(input: RBuilder): ReactElement {
//            return input.child(HelloComponent::class) {
//                attrs.objectName = objectName
//                attrs.name = name
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun State.init(props: Props) {
////        console.log("HelloComponent | State.init - ${props.name}")
//        name = props.name
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private fun onNameChange(newName: String) {
//        setState {
//            name = newName
//        }
//    }
//
//
//    private fun onSubmit() {
//        console.log("HelloComponent.onSubmit")
//
//        val rest = RestCommandApi()
//
//        async {
//            rest.edit(props.objectName, "name", state.name)
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun RBuilder.render() {
//        div {
//            +("[${props.objectName}] Hello: ")
//
//            input (type = InputType.text) {
//                attrs {
//                    value = state.name
//
//                    onChangeFunction = {
//                        val target = it.target as HTMLInputElement
//                        onNameChange(target.value)
//                    }
//                }
//            }
//
//            input (type = InputType.button) {
//                attrs {
//                    value = "Rename"
//                    onClickFunction = {
//                        onSubmit()
//                    }
//                }
//            }
//        }
//    }
//}
