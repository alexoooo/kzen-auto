package tech.kzen.auto.client.objects.document.script.display.view


import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeView
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewProps
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ControlSummaryAttributeViewState: State {
    var text: String?
}


//---------------------------------------------------------------------------------------------------------------------
// Collapsed-card summary for a ControlStep: reads the step's `action` and the `loop` reference (resolving the
// loop step's NAME, not the document name a plain ReferenceLinkAttributeView would show) and renders
// "Skip iteration -> LoopName" / "Finish loop -> LoopName". Tagged `summary:` on ControlStep.meta.action, but
// reads both sibling attributes off props.objectLocation.
@Suppress("unused")
class ControlSummaryAttributeView(
    props: AttributeViewProps
):
    ObjectScopedComponent<AttributeViewProps, ControlSummaryAttributeViewState>(props)
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
            ControlSummaryAttributeView::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val actionAttributePath = AttributePath.parse("action")
        private val loopAttributePath = AttributePath.parse("loop")

        private fun summaryText(action: String?, loopName: String?): String? {
            val actionLabel = when (action) {
                "skipIteration" -> "Skip iteration"
                "finishLoop" -> "Finish loop"
                else -> return null
            }
            return "$actionLabel → ${loopName ?: "?"}"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphStructure().graphNotation

        val action = graphNotation
            .firstAttribute(props.objectLocation, actionAttributePath)
            ?.asString()

        val loopName = graphNotation
            .firstAttribute(props.objectLocation, loopAttributePath)
            ?.asString()
            ?.takeIf { it.isNotEmpty() }
            ?.let { ObjectReference.parse(it) }
            ?.let { graphNotation.coalesce.locateOptional(it, ObjectReferenceHost.ofLocation(props.objectLocation)) }
            ?.objectPath
            ?.name
            ?.value

        val text = summaryText(action, loopName)

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
            ?: return

        div {
            css {
                color = Color("rgba(0, 0, 0, 0.55)")
                fontSize = 0.85.em
                whiteSpace = WhiteSpace.nowrap
                overflow = Overflow.hidden
                textOverflow = TextOverflow.ellipsis
                minWidth = 0.px
            }

            +text
        }
    }
}
