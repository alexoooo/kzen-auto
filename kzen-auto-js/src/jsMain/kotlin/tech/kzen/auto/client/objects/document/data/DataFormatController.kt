package tech.kzen.auto.client.objects.document.data

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.hr
import react.react
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.data.DataFormatConventions
import tech.kzen.auto.common.objects.document.data.spec.FieldFormatListSpec
import tech.kzen.auto.common.objects.document.data.spec.FieldFormatSpec
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.reflect.Reflect
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface DataFormatControllerState: State {
    var objectLocation: ObjectLocation?
    var fields: FieldFormatListSpec?
}


//---------------------------------------------------------------------------------------------------------------------
class DataFormatController(
    props: Props
):
    RComponent<Props, DataFormatControllerState>(props),
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
    override fun DataFormatControllerState.init(props: Props) {
        objectLocation = null
        fields = null
    }


    override fun componentDidMount() {
        ClientContext.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val documentPath = clientState.navigationRoute.documentPath
            ?: return

        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
            ?: return

        if (! DataFormatConventions.isDataFormat(documentNotation)) {
            return
        }

        setState {
            objectLocation = documentPath.toMainObjectLocation()
            fields = DataFormatConventions.fieldFormatListSpec(documentNotation)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAdd() {
        println("add")
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
                    key = fieldName
                    renderField(fieldName, fieldSpec)
                    hr {}
                }
            }

            DataFormatFieldAdd::class.react {
                this.objectLocation = objectLocation
            }
        }
    }


    private fun ChildrenBuilder.renderField(fieldName: String, fieldSpec: FieldFormatSpec) {
        div {
            +"Name: $fieldName"
        }

        renderMetadata(fieldSpec.typeMetadata)
    }


    private fun ChildrenBuilder.renderMetadata(typeMetadata: TypeMetadata) {
        div {
            +"ClassName: ${typeMetadata.className}"
        }
        div {
            +"Nullable: ${typeMetadata.nullable}"
        }
        div {
            +"Generics:"
            for ((index, genericType) in typeMetadata.generics.withIndex()) {
                div {
                    key = index.toString()
                    renderMetadata(genericType)
                }
            }
        }
    }
}