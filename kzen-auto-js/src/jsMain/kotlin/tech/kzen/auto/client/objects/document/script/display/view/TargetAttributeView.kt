package tech.kzen.auto.client.objects.document.script.display.view

import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import tech.kzen.auto.client.objects.document.common.attribute.AttributeView
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewProps
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.feature.TargetSpecDefiner
import tech.kzen.auto.common.objects.document.feature.TargetType
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface TargetAttributeViewProps: AttributeViewProps {
    var restClient: ClientRestApi
}


external interface TargetAttributeViewState: State {
    var targetType: TargetType?
    var targetValue: String?
    var visualLocation: ObjectLocation?
    var visualResourceUri: String?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class TargetAttributeView(
    props: TargetAttributeViewProps
):
    RPureComponent<TargetAttributeViewProps, TargetAttributeViewState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val restClient: ClientRestApi
    ):
        AttributeView(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeViewProps.() -> Unit) {
            TargetAttributeView::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                restClient = this@Wrapper.restClient
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

        val attributeNotation = graphStructure
            .graphNotation
            .firstAttribute(props.objectLocation, AttributePath.ofName(props.attributeName))
            as? MapAttributeNotation
            ?: return

        val targetType = attributeNotation
            .get(TargetSpecDefiner.typeKey)
            ?.asString()
            ?.let { TargetType.valueOf(it) }
            ?: return

        val targetValue = attributeNotation
            .get(TargetSpecDefiner.valueKey)
            ?.asString()

        val visualLocation: ObjectLocation? =
            if (targetType == TargetType.Visual && targetValue != null) {
                val host = ObjectReferenceHost.ofLocation(props.objectLocation)
                graphStructure.graphNotation.coalesce.locateOptional(
                    ObjectReference.parse(targetValue), host)
            }
            else {
                null
            }

        val visualResourceUri: String? =
            if (visualLocation != null) {
                val documentPath = visualLocation.documentPath
                val documentNotation = graphStructure.graphNotation.documents[documentPath]
                val firstResource = documentNotation?.resources?.digests?.keys?.firstOrNull()
                firstResource?.let {
                    props.restClient.resourceUri(ResourceLocation(documentPath, it))
                }
            }
            else {
                null
            }

        if (state.targetType == targetType &&
            state.targetValue == targetValue &&
            state.visualLocation == visualLocation &&
            state.visualResourceUri == visualResourceUri
        ) {
            return
        }

        setState {
            this.targetType = targetType
            this.targetValue = targetValue
            this.visualLocation = visualLocation
            this.visualResourceUri = visualResourceUri
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val targetType = state.targetType
            ?: return

        if (targetType == TargetType.Visual) {
            renderVisual()
        }
        else {
            renderTextual(targetType)
        }
    }


    private fun ChildrenBuilder.renderTextual(targetType: TargetType) {
        val label = when (targetType) {
            TargetType.Focus ->
                "Currently focused"

            TargetType.Text -> {
                val value = state.targetValue ?: ""
                "Containing text \"$value\""
            }

            TargetType.Xpath -> {
                val value = state.targetValue ?: ""
                "Matching XPath $value"
            }

            TargetType.Visual ->
                // NB: handled by renderVisual, but kept here as defensive fallback for the
                // case where the Visual reference can't be resolved
                "Visual ${state.visualLocation?.documentPath?.name?.value ?: ""}"
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

            +label
        }
    }


    private fun ChildrenBuilder.renderVisual() {
        val resourceUri = state.visualResourceUri
        if (resourceUri == null) {
            renderTextual(TargetType.Visual)
            return
        }

        img {
            css {
                maxHeight = 2.em
                maxWidth = 100.pct
                objectFit = ObjectFit.contain
                display = Display.block
            }
            src = resourceUri
        }
    }
}
