package tech.kzen.auto.client.objects.document.script.step.display.control

//import react.RBuilder
//import react.RHandler
//import react.RState
//import react.ReactElement
//import tech.kzen.auto.client.objects.document.common.AttributeController
//import tech.kzen.auto.client.objects.document.script.step.display.DefaultStepDisplay
//import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayProps
//import tech.kzen.auto.client.objects.document.script.step.display.StepDisplayWrapper
//import tech.kzen.auto.client.wrap.RPureComponent
//import tech.kzen.lib.common.model.locate.ObjectLocation
//
//
//@Suppress("unused")
//class MappingItemDisplay(
//        props: Props
//):
//        RPureComponent<MappingItemDisplay.Props, RState>(props)
//{
//    //-----------------------------------------------------------------------------------------------------------------
//    class Props(
//            var attributeController: AttributeController.Wrapper,
//
//            common: Common
//    ): StepDisplayProps(common)
//
//
//    @Suppress("unused")
//    class Wrapper(
//            objectLocation: ObjectLocation,
//            private val attributeController: AttributeController.Wrapper
//    ) :
//            StepDisplayWrapper(objectLocation) {
//        override fun child(
//                input: RBuilder,
//                handler: RHandler<StepDisplayProps>
//        ): ReactElement {
//            return input.child(MappingItemDisplay::class) {
//                attrs {
//                    attributeController = this@Wrapper.attributeController
//                }
//
//                handler()
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun RBuilder.render() {
//        child(DefaultStepDisplay::class) {
//            attrs {
//                attributeController = props.attributeController
//                common = props.common
//            }
//        }
//    }
//}