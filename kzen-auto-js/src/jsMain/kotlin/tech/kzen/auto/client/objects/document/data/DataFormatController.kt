package tech.kzen.auto.client.objects.document.data

import react.ChildrenBuilder
import react.Props
import react.State
import react.react
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


//---------------------------------------------------------------------------------------------------------------------
external interface DataFormatControllerState: State {
    var clientState: ClientState?
//    var detailList: List<ReportDefinerDetail>?
//    var listingError: String?
}


//---------------------------------------------------------------------------------------------------------------------
class DataFormatController(
    props: Props
):
    RPureComponent<Props, DataFormatControllerState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }


        override fun header(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {}
            }
        }


        override fun body(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    DataFormatController::class.react {
                        block()
                    }
                }
            }
        }
    }



    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {

    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        +"[Data Format]"
    }
}