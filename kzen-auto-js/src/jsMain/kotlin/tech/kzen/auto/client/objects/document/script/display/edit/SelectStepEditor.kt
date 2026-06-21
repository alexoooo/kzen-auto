package tech.kzen.auto.client.objects.document.script.display.edit

import emotion.react.css
import js.objects.unsafeJso
import mui.material.InputLabel
import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreKey
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.client.wrap.select.reactSelectField
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedObjectRefactorEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface SelectStepEditorState: State {
    var value: ObjectLocation?
    var renaming: Boolean

    var initialized: Boolean
    var predecessors: List<ObjectLocation>?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class SelectStepEditor(
    props: AttributeEditorProps
):
    RPureComponent<AttributeEditorProps, SelectStepEditorState>(props),
    LocalGraphStore.Observer,
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
    override fun SelectStepEditorState.init(props: AttributeEditorProps) {
        value = null
        renaming = false
        initialized = false
        predecessors = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
        prevProps: AttributeEditorProps,
        prevState: SelectStepEditorState,
        snapshot: Any
    ) {
        if (state.value != prevState.value) {
            if (state.renaming) {
                setState {
                    renaming = false
                }
            }
            else if (prevState.initialized) {
                editAttributeCommandAsync()
            }
        }
    }


    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
        contextValue<DocumentBridge?>()?.lookup(ScriptStoreKey)?.observe(this)
        async {
            props.mirroredGraphStore.observe(this)
        }
    }


    override fun componentWillUnmount() {
        props.mirroredGraphStore.unobserve(this)
        contextValue<DocumentBridge?>()?.lookup(ScriptStoreKey)?.unobserve(this)
        props.clientStateGlobal.unobserve(this)
    }


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

        setState {
            this.value = value
            initialized = true
        }
    }


    override fun onScriptState(scriptState: ScriptState) {
        val scriptTree = scriptState.scriptTree
        val targetPath = props.objectLocation.objectPath

        // Prior body steps plus the in-scope value bindings (parameters / loop items) — any of which this
        // input can reference, since a binding is an addressable, typed value just like a step output.
        val candidatePaths = scriptTree.predecessors(targetPath) + scriptTree.inScopeBindingPaths(targetPath)
        val predecessors = candidatePaths.map { props.objectLocation.documentPath.toObjectLocation(it) }

        if (state.predecessors != predecessors) {
            setState {
                this.predecessors = predecessors
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        when (event) {
            is RenamedObjectRefactorEvent -> {
                if (event.renamedObject.objectLocation == state.value) {
                    setState {
                        value = event.renamedObject.newObjectLocation()
                        renaming = true
                    }
                }
            }

            else -> {}
        }
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {}


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(value: ObjectLocation?) {
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

        val localReference = value.toReference()
                .crop(retainPath = false)

        props.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                props.attributeName,
                ScalarAttributeNotation(localReference.asString())))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val predecessors = state.predecessors
            ?: return

        val selectOptions: Array<ReactSelectOption> = predecessors
            .map { location ->
                val option: ReactSelectOption = unsafeJso {
                    this.value = location.asString()
                    this.label = location.objectPath.name.value
                }
                option
            }
            .toTypedArray()

        val selectedValue = selectOptions.find { it.value == state.value?.asString() }

        InputLabel {
            css {
                fontSize = 0.8.em
            }

            +formattedLabel()

            reactSelectField(
                selectedOption = selectedValue,
                options = selectOptions,
                onSelect = { onValueChange(ObjectLocation.parse(it.value)) })
        }
    }


    private fun formattedLabel(): String {
        return CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
    }
}
