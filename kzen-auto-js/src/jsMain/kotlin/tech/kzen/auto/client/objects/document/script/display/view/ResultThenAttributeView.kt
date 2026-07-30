package tech.kzen.auto.client.objects.document.script.display.view


import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.attribute.AttributeView
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewProps
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ResultThenAttributeViewState: State {
    var endScript: Boolean?
}


//---------------------------------------------------------------------------------------------------------------------
// Collapsed-card summary for a ResultStep's `then`: renders a small "End script" chip ONLY when
// then == endScript, and nothing for the default keepRunning — so the default ResultStep card is unchanged
// (its `code` keeps its own TextAttributeView summary) and only the return-from-document case is flagged.
@Suppress("unused")
class ResultThenAttributeView(
    props: AttributeViewProps
):
    ObjectScopedComponent<AttributeViewProps, ResultThenAttributeViewState>(props)
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
            ResultThenAttributeView::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val endScriptValue = "endScript"
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphStructure().graphNotation

        val endScript = graphNotation
            .firstAttribute(props.objectLocation, AttributePath.ofName(props.attributeName))
            ?.asString() == endScriptValue

        if (state.endScript == endScript) {
            return
        }

        setState {
            this.endScript = endScript
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        if (state.endScript != true) {
            return
        }

        span {
            css {
                display = Display.inlineBlock
                padding = Padding(0.px, 0.5.em)
                borderRadius = 0.85.em
                fontSize = 0.72.em
                lineHeight = number(1.5)
                whiteSpace = WhiteSpace.nowrap
                backgroundColor = Color("rgba(0, 0, 0, 0.08)")
                color = Color("rgba(0, 0, 0, 0.6)")
            }

            +"End script"
        }
    }
}
