package tech.kzen.auto.client.objects.document.data.schema

import react.ChildrenBuilder
import react.Key
import react.dom.html.ReactHTML.hr
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaFieldListSpec
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore


class DataSchemaFieldsEditor(
    props: AttributeEditorProps
): ObjectScopedComponent<AttributeEditorProps, DataSchemaFieldsEditorState>(props) {
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ): AttributeEditor(objectLocation) {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            DataSchemaFieldsEditor::class.react {
                this.clientStateGlobal = this@Wrapper.clientStateGlobal
                this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    override fun onClientState(clientState: ClientState) {
        val notation = clientState.graphStructure().graphNotation
            .firstAttribute(props.objectLocation, props.attributeName) as? MapAttributeNotation
            ?: return
        val fields = DataSchemaFieldListSpec.ofAttributeNotation(notation).fields
        if (fields == state.fields) {
            return
        }
        setState {
            this.fields = fields
        }
    }


    override fun ChildrenBuilder.render() {
        for ((fieldName, fieldSpec) in state.fields.orEmpty()) {
            DataSchemaFieldEdit::class.react {
                key = Key(fieldName)
                objectLocation = props.objectLocation
                this.fieldName = fieldName
                this.fieldSpec = fieldSpec
            }
            hr {}
        }
        DataSchemaFieldAdd::class.react {
            objectLocation = props.objectLocation
            mirroredGraphStore = this@DataSchemaFieldsEditor.props.mirroredGraphStore
        }
    }
}
