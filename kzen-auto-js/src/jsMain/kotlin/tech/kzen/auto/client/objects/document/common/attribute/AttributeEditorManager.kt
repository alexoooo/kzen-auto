package tech.kzen.auto.client.objects.document.common.attribute

import react.ChildrenBuilder
import react.Props
import react.State
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


//---------------------------------------------------------------------------------------------------------------------
// NB: the manager's own dispatch contract, not a subtype of AttributeEditorProps - hosts set only the two
// addressing fields, and the Wrapper supplies the rest. Inheriting the editor contract dragged in a
// mirroredGraphStore that nothing ever set.
external interface AttributeEditorManagerProps: Props {
    var objectLocation: ObjectLocation
    var attributeName: AttributeName

    var clientStateGlobal: ClientStateGlobal
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
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val attributeEditors: List<AttributeEditor>,
        @Service private val clientStateGlobal: ClientStateGlobal
    ):
        ReactWrapper<AttributeEditorManagerProps>
    {
        override fun ChildrenBuilder.child(block: AttributeEditorManagerProps.() -> Unit) {
            AttributeEditorManager::class.react {
                this.attributeEditors = this@Wrapper.attributeEditors
                clientStateGlobal = this@Wrapper.clientStateGlobal
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
    }


    override fun onClientState(clientState: ClientState) {
        if (state.attributeEditor != null) {
            return
        }

        val editorWrapperName = AttributeWrapperLookup.wrapperName(
            clientState.graphStructure(),
            props.objectLocation,
            props.attributeName,
            AttributeWrapperLookup.editorAttributePath
        ) ?: DefaultAttributeEditor.wrapperName

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