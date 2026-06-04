package tech.kzen.auto.client.objects.document.common.attribute

import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface TextAttributeViewState: State {
    var text: String?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class TextAttributeView(
    props: AttributeViewProps
):
    RPureComponent<AttributeViewProps, TextAttributeViewState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal
    ):
        AttributeView(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeViewProps.() -> Unit) {
            TextAttributeView::class.react {
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
        val graphStructure = clientState.graphStructure()

        if (props.objectLocation !in graphStructure.graphNotation.coalesce) {
            // NB: containing step was renamed or deleted; parent re-render will swap props.objectLocation shortly
            return
        }

        val text = graphStructure
            .graphNotation
            .firstAttribute(props.objectLocation, AttributePath.ofName(props.attributeName))
            ?.asString()

        if (state.text == text) {
            return
        }

        setState {
            this.text = text
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val text = state.text
        if (text.isNullOrEmpty()) {
            return
        }

        div {
            css {
                color = Color("rgba(0, 0, 0, 0.55)")
                fontSize = 0.85.em
                whiteSpace = WhiteSpace.nowrap
                overflow = Overflow.hidden
                textOverflow = TextOverflow.ellipsis
                minWidth = 0.px
            }

            +"${props.attributeName.value}: $text"
        }
    }
}
