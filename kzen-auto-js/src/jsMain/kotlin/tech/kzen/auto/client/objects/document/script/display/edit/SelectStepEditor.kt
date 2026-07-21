package tech.kzen.auto.client.objects.document.script.display.edit

import js.objects.unsafeJso
import react.ChildrenBuilder
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.select.SelectReferenceEditorBase
import tech.kzen.auto.client.objects.document.common.edit.select.SelectReferenceEditorState
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreKey
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedObjectRefactorEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore


//---------------------------------------------------------------------------------------------------------------------
// Picks the step (or in-scope value binding) that a step input attribute references, from the steps that precede
// it in the Script. Option keys are full ObjectLocation strings; the wire form is cropped to a bare name, since
// the reference always resolves within the same document.
@Suppress("unused")
class SelectStepEditor(
    props: AttributeEditorProps
):
    SelectReferenceEditorBase<AttributeEditorProps, SelectReferenceEditorState>(props),
    ClientStateGlobal.Observer,
    ScriptStore.Observer
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
            SelectStepEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onMount() {
        props.clientStateGlobal.observe(this)
        contextValue<DocumentBridge?>()?.lookup(ScriptStoreKey)?.observe(this)
    }


    override fun onUnmount() {
        contextValue<DocumentBridge?>()?.lookup(ScriptStoreKey)?.unobserve(this)
        props.clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphStructure().graphNotation

        if (props.objectLocation !in graphNotation.coalesce) {
            // NB: containing step deleted or renamed and this objectLocation is stale
            return
        }

        val attributeNotation = graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)

        val objectReferenceHost = ObjectReferenceHost.ofLocation(props.objectLocation)

        val value =
            (attributeNotation as? ScalarAttributeNotation)?.let {
                val reference = ObjectReference.parse(it.value)
                graphNotation.coalesce
                    .locateOptional(reference, objectReferenceHost)
            }

        setSelected(value?.asString())
    }


    override fun onScriptState(scriptState: ScriptState) {
        val scriptTree = scriptState.scriptTree
        val targetPath = props.objectLocation.objectPath

        // Prior body steps plus the in-scope value bindings (parameters / loop items) — any of which this
        // input can reference, since a binding is an addressable, typed value just like a step output.
        val candidatePaths = scriptTree.inScopeReferencePaths(targetPath)

        setOptions(candidatePaths
            .map { objectPath ->
                val location = props.objectLocation.documentPath.toObjectLocation(objectPath)
                val option: SelectOption = unsafeJso {
                    value = location.asString()
                    label = objectPath.name.value
                }
                option
            }
            .toTypedArray())
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onNotationEvent(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        if (event is RenamedObjectRefactorEvent &&
                event.renamedObject.objectLocation.asString() == state.selected
        ) {
            setSelected(event.renamedObject.newObjectLocation().asString())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun wireValue(optionKey: String): String {
        return ObjectLocation.parse(optionKey)
            .toReference()
            .crop(retainPath = false)
            .asString()
    }
}
