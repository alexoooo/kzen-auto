package tech.kzen.auto.client.objects.document.common.attribute

import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.metadata.AttributeMetadata
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


//---------------------------------------------------------------------------------------------------------------------
external interface AttributeViewManagerProps: AttributeViewProps {
    var attributeViews: List<AttributeView>
}


external interface AttributeViewManagerState: State {
    var attributeViewName: ObjectName?
    var attributeView: AttributeView?
}


//---------------------------------------------------------------------------------------------------------------------
class AttributeViewManager(
    props: AttributeViewManagerProps
):
    RPureComponent<AttributeViewManagerProps, AttributeViewManagerState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val summaryAttributePath = AttributePath.parse("summary")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val attributeViews: List<AttributeView>,
        @Service private val clientStateGlobal: ClientStateGlobal
    ):
        ReactWrapper<AttributeViewManagerProps>
    {
        override fun ChildrenBuilder.child(block: AttributeViewManagerProps.() -> Unit) {
            AttributeViewManager::class.react {
                this.attributeViews = this@Wrapper.attributeViews
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
        if (state.attributeView != null) {
            return
        }

        val attributeMetadata: AttributeMetadata? = clientState
            .graphStructure()
            .graphMetadata
            .get(props.objectLocation)
            ?.attributes
            ?.get(props.attributeName)

        val viewAttributeNotation = attributeMetadata
            ?.attributeMetadataNotation
            ?.get(summaryAttributePath.toNesting())

        val viewWrapperName = viewAttributeNotation
            ?.asString()
            ?.let { ObjectName(it) }
            ?: return

        val attributeView =
            props.attributeViews.find { it.name() == viewWrapperName }

        setState {
            this.attributeViewName = viewWrapperName
            this.attributeView = attributeView
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val viewWrapper = state.attributeView
            ?: return

        viewWrapper.child(this) {
            objectLocation = props.objectLocation
            attributeName = props.attributeName
        }
    }
}
