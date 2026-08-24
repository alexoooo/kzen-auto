package tech.kzen.auto.client.objects.document.data.schema

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
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaConventions
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldListSpec
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface DataSchemaControllerProps: Props {
    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}


external interface DataSchemaControllerState: State {
    var objectLocation: ObjectLocation?
    var fields: DataSchemaFieldListSpec?
}


//---------------------------------------------------------------------------------------------------------------------
class DataSchemaController(
    props: DataSchemaControllerProps
):
    RComponent<DataSchemaControllerProps, DataSchemaControllerState>(props),
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
                    DataSchemaController::class.react {
                        clientStateGlobal = this@Wrapper.clientStateGlobal
                        mirroredGraphStore = this@Wrapper.mirroredGraphStore
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun DataSchemaControllerState.init(props: DataSchemaControllerProps) {
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

        if (!DataSchemaConventions.isDataSchema(documentNotation)) {
            return
        }

        setState {
            objectLocation = documentPath.toMainObjectLocation()
            fields = DataSchemaConventions.fieldListSpec(documentNotation)
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

                    DataSchemaFieldEdit::class.react {
                        this.objectLocation = objectLocation
                        this.fieldName = fieldName
                        this.fieldSpec = fieldSpec
                    }

                    hr {}
                }
            }

            DataSchemaFieldAdd::class.react {
                this.objectLocation = objectLocation
                this.mirroredGraphStore = props.mirroredGraphStore
            }
        }
    }
}
