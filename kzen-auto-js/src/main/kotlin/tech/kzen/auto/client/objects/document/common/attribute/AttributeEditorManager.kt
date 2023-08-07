package tech.kzen.auto.client.objects.document.common.attribute

import react.ChildrenBuilder
import react.State
import react.react
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.common.*
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.metadata.AttributeMetadata
import tech.kzen.lib.common.reflect.Reflect


//---------------------------------------------------------------------------------------------------------------------
external interface AttributeEditorManagerProps: AttributeEditor2Props {
    var attributeEditors: List<AttributeEditor>
}


external interface AttributeEditorManagerState: State {
    var attributeEditorName: ObjectName?
    var attributeEditor: AttributeEditor?
}


//---------------------------------------------------------------------------------------------------------------------
class AttributeEditorManager(
    props: AttributeEditorManagerProps
):
    RPureComponent<AttributeEditorManagerProps, AttributeEditorManagerState>(props),
    SessionGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val editorAttributePath = AttributePath.parse("editor")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val attributeEditors: List<AttributeEditor>
    ):
        ReactWrapper<AttributeEditorManagerProps>
    {
        override fun ChildrenBuilder.child(block: AttributeEditorManagerProps.() -> Unit) {
            AttributeEditorManager::class.react {
                this.attributeEditors = this@Wrapper.attributeEditors
                block()
            }
        }
    }



    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.sessionGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.sessionGlobal.unobserve(this)
    }


    override fun onClientState(clientState: SessionState) {
        if (state.attributeEditor != null) {
            return
        }

        val attributeMetadata: AttributeMetadata? = clientState
            .graphStructure()
            .graphMetadata
            .get(props.objectLocation)
            ?.attributes
            ?.get(props.attributeName)

        val editorAttributeNotation = attributeMetadata
            ?.attributeMetadataNotation
            ?.get(editorAttributePath.toNesting())

        val editorWrapperName = editorAttributeNotation
            ?.asString()
            ?.let { ObjectName(it) }
            ?: AutoAttributeEditor.wrapperName

        val attributeEditor =
            props.attributeEditors.find { it.name() == editorWrapperName }

        setState {
            this.attributeEditorName = editorWrapperName
            this.attributeEditor = attributeEditor
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val editorWrapper = state.attributeEditor

//        +"[Foo]"
        if (editorWrapper == null) {
            +"[Attribute editor not found: ${state.attributeEditorName}]"
        }
        else {
//            +"editor ${editorWrapper.name()}"
            editorWrapper.child(this) {
                objectLocation = props.objectLocation
                attributeName = props.attributeName
            }
        }
    }
}