package tech.kzen.auto.client.objects.document.script.display.view

import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.common.attribute.AttributeView
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewProps
import tech.kzen.auto.client.objects.document.script.display.target.TargetSummaryContext
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.objects.document.script.display.target.TargetTypeDisplay
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.target.TargetSpecDefiner
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


//---------------------------------------------------------------------------------------------------------------------
external interface TargetAttributeViewProps: AttributeViewProps {
    var restClient: ClientRestApi
    var targetTypes: List<TargetTypeDisplay>
}


external interface TargetAttributeViewState: State {
    var typeName: String?
    var value: String?

    // Whatever else the type's summary derives from the graph (e.g. the referenced document's
    // first crop) — tracked so the graph-store broadcast only re-renders when the summary changes
    var summaryDependencies: String?
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Type-agnostic host of the `target:` collapsed summary: resolves the registered
 * [TargetTypeDisplay] by the notation's `type:` and delegates the rendering to it.
 */
@Suppress("unused")
class TargetAttributeView(
    props: TargetAttributeViewProps
):
    ObjectScopedComponent<TargetAttributeViewProps, TargetAttributeViewState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val targetTypes: List<TargetTypeDisplay>,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val restClient: ClientRestApi
    ):
        AttributeView(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeViewProps.() -> Unit) {
            TargetAttributeView::class.react {
                targetTypes = this@Wrapper.targetTypes
                clientStateGlobal = this@Wrapper.clientStateGlobal
                restClient = this@Wrapper.restClient
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val graphStructure = clientState.graphStructure()

        val attributeNotation = graphStructure
            .graphNotation
            .firstAttribute(props.objectLocation, AttributePath.ofName(props.attributeName))
            as? MapAttributeNotation
            ?: return

        val typeName = attributeNotation
            .get(TargetSpecDefiner.typeKey)
            ?.asString()
            ?: return

        val value = attributeNotation
            .get(TargetSpecDefiner.valueKey)
            ?.asString()

        val summaryDependencies = props.targetTypes
            .find { it.typeName == typeName }
            ?.summaryDependencies(value, clientState, props.objectLocation)

        if (state.typeName == typeName &&
            state.value == value &&
            state.summaryDependencies == summaryDependencies
        ) {
            return
        }

        setState {
            this.typeName = typeName
            this.value = value
            this.summaryDependencies = summaryDependencies
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val typeName = state.typeName
            ?: return

        val display = props.targetTypes.find { it.typeName == typeName }
            ?: return

        val graphStructure = props.clientStateGlobal.current()?.graphStructure()
            ?: return

        val context = TargetSummaryContext(
            value = state.value,
            objectLocation = props.objectLocation,
            graphStructure = graphStructure,
            restClient = props.restClient)

        with(display) {
            renderSummary(context)
        }
    }
}
