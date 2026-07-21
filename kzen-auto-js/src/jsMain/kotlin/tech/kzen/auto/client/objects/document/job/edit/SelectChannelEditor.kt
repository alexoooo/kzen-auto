package tech.kzen.auto.client.objects.document.job.edit

import js.objects.unsafeJso
import react.ChildrenBuilder
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.select.SelectReferenceEditorBase
import tech.kzen.auto.client.objects.document.common.edit.select.SelectReferenceEditorState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.MirroredGraphStore


//---------------------------------------------------------------------------------------------------------------------
// Picks the Channel a Worker's endpoint attribute (ChannelInput / ChannelOutput / ChannelServer /
// ChannelClient) references, from the Job document's declared Channels. Wired via `editor: SelectChannelEditor`
// in the Worker archetype metadata. The endpoint TYPE differs from the Channel object type (the JobChannelCreator
// bridges them), so the generic SelectObjectEditor — which matches by the attribute's `is:` constraint — can't be
// reused here; this lists Channel objects directly.
//
// The channel path IS the wire form, so option key and notation value coincide (identity wireValue).
@Suppress("unused")
class SelectChannelEditor(
    props: AttributeEditorProps
):
    SelectReferenceEditorBase<AttributeEditorProps, SelectReferenceEditorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            SelectChannelEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Hydrates synchronously, so the field is populated on first paint rather than after a mount round-trip.
    override fun SelectReferenceEditorState.init(props: AttributeEditorProps) {
        val graphNotation = props.clientStateGlobal.current()!!.graphStructure().graphNotation
        selected = currentReference(graphNotation)
        options = channelOptions(graphNotation)
    }


    private fun currentReference(graphNotation: GraphNotation): String? {
        val attributeNotation = graphNotation.firstAttribute(props.objectLocation, props.attributeName)
        return (attributeNotation as? ScalarAttributeNotation)?.value?.takeIf { it.isNotEmpty() }
    }


    private fun channelOptions(graphNotation: GraphNotation): Array<SelectOption> {
        val documentNotation = graphNotation.documents[props.objectLocation.documentPath]
            ?: return arrayOf()

        return documentNotation
            .directNestedObjectPaths(NotationConventions.mainObjectPath, JobConventions.channelsAttributeName)
            .map { channelPath ->
                val option: SelectOption = unsafeJso {
                    value = channelPath.asString()
                    label = channelPath.name.value
                }
                option
            }
            .toTypedArray()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Keep the channel list in sync as Channels are added / removed elsewhere in the document.
    override suspend fun onNotationEvent(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        setOptions(channelOptions(graphDefinition.graphStructure.graphNotation))
    }


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        setOptions(channelOptions(graphDefinitionAttempt.graphStructure.graphNotation))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun wireValue(optionKey: String): String {
        return optionKey
    }
}
