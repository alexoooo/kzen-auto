package tech.kzen.auto.client.objects.document.common.attribute

import emotion.react.css
import js.objects.unsafeJso
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ReferenceLinkAttributeViewState: State {
    // The document the attribute's reference resolves to (null when empty/unresolved).
    var documentPath: DocumentPath?
}


//---------------------------------------------------------------------------------------------------------------------
// Summary view for an attribute that holds an ObjectLocation reference (e.g. RunStep.instructions):
// shows the referenced document's NAME as a link that navigates to it — rather than the raw reference
// text TextAttributeView would print. Navigation rides the hash via NavigationRoute, so no service
// beyond clientStateGlobal (used to resolve the reference) is needed.
@Suppress("unused")
class ReferenceLinkAttributeView(
    props: AttributeViewProps
):
    RPureComponent<AttributeViewProps, ReferenceLinkAttributeViewState>(props),
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
            ReferenceLinkAttributeView::class.react {
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


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphStructure().graphNotation

        if (props.objectLocation !in graphNotation.coalesce) {
            // NB: containing step was renamed or deleted; parent re-render will swap props.objectLocation shortly
            return
        }

        val reference = graphNotation
            .firstAttribute(props.objectLocation, AttributePath.ofName(props.attributeName))
            ?.asString()
            ?.takeIf { it.isNotEmpty() }
            ?.let { ObjectReference.parse(it) }

        val documentPath = reference
            ?.let { graphNotation.coalesce.locateOptional(it, ObjectReferenceHost.ofLocation(props.objectLocation)) }
            ?.documentPath

        if (state.documentPath == documentPath) {
            return
        }

        setState {
            this.documentPath = documentPath
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val documentPath = state.documentPath
            ?: return

        a {
            css {
                display = Display.inlineFlex
                alignItems = AlignItems.center
                maxWidth = 100.pct
                minWidth = 0.px

                fontSize = 0.85.em
                color = Color("rgba(0, 0, 0, 0.55)")
                textDecoration = Globals.initial
                cursor = Cursor.pointer

                "&:hover" {
                    color = Color("#1565ff")
                }
            }

            href = NavigationRoute(documentPath, RequestParams.empty).toFragment()

            // Navigate via the hash, but don't let the click bubble to the step card's expand toggle.
            onClick = { it.stopPropagation() }

            // The script name, ellipsized if long so a deep document path can't widen the card.
            span {
                css {
                    overflow = Overflow.hidden
                    textOverflow = TextOverflow.ellipsis
                    whiteSpace = WhiteSpace.nowrap
                    minWidth = 0.px
                }
                +documentPath.name.value
            }

            icon("material-symbols:open-in-new") {
                style = unsafeJso {
                    fontSize = 1.em
                    marginLeft = 0.25.em
                }
            }
        }
    }
}
