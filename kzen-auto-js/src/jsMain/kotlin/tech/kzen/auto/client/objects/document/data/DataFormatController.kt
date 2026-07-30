package tech.kzen.auto.client.objects.document.data

import emotion.react.css
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.hr
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.data.DataFormatConventions
import tech.kzen.auto.common.objects.document.data.spec.FieldFormatListSpec
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface DataFormatControllerProps: Props {
    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}


external interface DataFormatControllerState: State {
    var objectLocation: ObjectLocation?
    var fields: FieldFormatListSpec?
}


//---------------------------------------------------------------------------------------------------------------------
class DataFormatController(
    props: DataFormatControllerProps
):
    RComponent<DataFormatControllerProps, DataFormatControllerState>(props),
    ClientStateGlobal.DocumentScopedObserver
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
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
                        clientStateGlobal = this@Wrapper.clientStateGlobal
                        mirroredGraphStore = this@Wrapper.mirroredGraphStore
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun DataFormatControllerState.init(props: DataFormatControllerProps) {
        objectLocation = null
        fields = null
    }


    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return

        if (!DataFormatConventions.isDataFormat(documentNotation)) {
            return
        }

        setState {
            objectLocation = documentPath.toMainObjectLocation()
            fields = DataFormatConventions.fieldFormatListSpec(documentNotation)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val objectLocation = state.objectLocation ?: return
        val fields = state.fields ?: return

        div {
            css {
                padding = 1.em
            }

            for ((fieldName, fieldSpec) in fields.fields) {
                div {
                    key = Key(fieldName)

                    DataFormatFieldEdit::class.react {
                        this.objectLocation = objectLocation
                        this.fieldName = fieldName
                        this.fieldSpec = fieldSpec
                    }

                    hr {}
                }
            }

            DataFormatFieldAdd::class.react {
                this.objectLocation = objectLocation
                this.mirroredGraphStore = props.mirroredGraphStore
            }
        }
    }
}