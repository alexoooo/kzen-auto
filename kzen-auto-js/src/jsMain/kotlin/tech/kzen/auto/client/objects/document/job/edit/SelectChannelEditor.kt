package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import js.objects.unsafeJso
import mui.material.InputLabel
import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.client.wrap.select.reactSelectField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface SelectChannelEditorState: State {
    // The current channel reference string (e.g. "main.channels/raw"), or null when unset.
    var value: String?

    // Channel object paths in this Job document, in document order.
    var options: List<ObjectPath>?
}


//---------------------------------------------------------------------------------------------------------------------
// Picks the Channel a Worker's endpoint attribute (ChannelInput / ChannelOutput / ChannelServer /
// ChannelClient) references, from the Job document's declared Channels. Wired via `editor: SelectChannelEditor`
// in the Worker archetype metadata. The endpoint TYPE differs from the Channel object type (the JobChannelCreator
// bridges them), so the generic SelectObjectEditor — which matches by the attribute's `is:` constraint — can't be
// reused here; this lists Channel objects directly.
@Suppress("unused")
class SelectChannelEditor(
    props: AttributeEditorProps
):
    RComponent<AttributeEditorProps, SelectChannelEditorState>(props),
    LocalGraphStore.Observer
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
    override fun SelectChannelEditorState.init(props: AttributeEditorProps) {
        val graphNotation = props.clientStateGlobal.current()!!.graphStructure().graphNotation
        value = currentReference(graphNotation)
        options = channelOptions(graphNotation)
    }


    private fun currentReference(graphNotation: GraphNotation): String? {
        val attributeNotation = graphNotation.firstAttribute(props.objectLocation, props.attributeName)
        return (attributeNotation as? ScalarAttributeNotation)?.value?.takeIf { it.isNotEmpty() }
    }


    private fun channelOptions(graphNotation: GraphNotation): List<ObjectPath> {
        val documentNotation = graphNotation.documents[props.objectLocation.documentPath]
            ?: return listOf()
        return documentNotation.directNestedObjectPaths(
            NotationConventions.mainObjectPath, JobConventions.channelsAttributeName)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
            props.mirroredGraphStore.observe(this)
        }
    }


    override fun componentWillUnmount() {
        props.mirroredGraphStore.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
        prevProps: AttributeEditorProps,
        prevState: SelectChannelEditorState,
        snapshot: Any
    ) {
        // Write only when the USER changed the selection — the initial value comes from init (sync), so there's
        // no spurious echo write on mount.
        if (state.value != prevState.value && state.value != null) {
            editAttributeCommandAsync()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        refreshOptions(graphDefinition.graphStructure.graphNotation)
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        refreshOptions(graphDefinitionAttempt.graphStructure.graphNotation)
    }


    // Keep the channel list in sync as Channels are added / removed elsewhere in the document.
    private fun refreshOptions(graphNotation: GraphNotation) {
        val nextOptions = channelOptions(graphNotation)
        if (state.options != nextOptions) {
            setState {
                options = nextOptions
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(value: String) {
        setState {
            this.value = value
        }
    }


    private fun editAttributeCommandAsync() {
        async {
            editAttributeCommand()
        }
    }


    private suspend fun editAttributeCommand() {
        val value = state.value
            ?: return

        props.mirroredGraphStore.apply(UpsertAttributeCommand(
            props.objectLocation,
            props.attributeName,
            ScalarAttributeNotation(value)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val options = state.options
            ?: return

        val selectOptions = options
            .map { channelPath ->
                val option: ReactSelectOption = unsafeJso {
                    value = channelPath.asString()
                    label = channelPath.name.value
                }
                option
            }
            .toTypedArray()

        InputLabel {
            css {
                fontSize = 0.8.em
            }

            +formattedLabel()

            reactSelectField(
                selectedOption = selectOptions.find { it.value == state.value },
                options = selectOptions,
                onSelect = { onValueChange(it.value) })
        }
    }


    private fun formattedLabel(): String {
        return CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
    }
}
