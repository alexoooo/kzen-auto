package tech.kzen.auto.client.objects.document.script.display.edit

import js.objects.unsafeJso
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
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.common.objects.document.script.model.ScriptNestingAnalysis
import tech.kzen.auto.common.objects.document.script.model.ScriptTree
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedObjectRefactorEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore


//---------------------------------------------------------------------------------------------------------------------
external interface SelectEnclosingLoopEditorState: State {
    var value: ObjectLocation?
    var renaming: Boolean

    var initialized: Boolean
    var candidates: List<ObjectLocation>?
}


//---------------------------------------------------------------------------------------------------------------------
// Loop-target dropdown for ControlStep.loop: unlike SelectStepEditor (which lists a step's predecessors), the
// candidate set is the ENCLOSING loops from ScriptNestingAnalysis.enclosingLoops — the exact set ControlStep's
// server-side definition() validates against, so dropdown and validation stay in lock-step. On a fresh (empty)
// loop it pre-fills the innermost enclosing loop, so an inserted-and-expanded ControlStep is valid by default.
@Suppress("unused")
class SelectEnclosingLoopEditor(
    props: AttributeEditorProps
):
    RPureComponent<AttributeEditorProps, SelectEnclosingLoopEditorState>(props),
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
            SelectEnclosingLoopEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // enclosingLoops needs both graphNotation (ClientState) and scriptTree (ScriptStore); cache each as it
    // arrives (plain fields, not state — the large graphNotation would defeat the shallow-equal and re-render
    // on every keystroke) and recompute the candidate list when both are present.
    private var latestGraphNotation: GraphNotation? = null
    private var latestScriptTree: ScriptTree? = null

    // Plain-field mirror of the resolved loop value + initialized flag, so the pre-fill guard reads the
    // freshly-resolved emptiness rather than the not-yet-applied `state` (setState is async, so `state.value`
    // is stale within the callback that scheduled it).
    private var latestResolvedValue: ObjectLocation? = null
    private var latestInitialized = false
    private var defaultApplied = false


    //-----------------------------------------------------------------------------------------------------------------
    override fun SelectEnclosingLoopEditorState.init(props: AttributeEditorProps) {
        value = null
        renaming = false
        initialized = false
        candidates = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
        prevProps: AttributeEditorProps,
        prevState: SelectEnclosingLoopEditorState,
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

        latestGraphNotation = graphNotation

        val attributeNotation = graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)

        val objectReferenceHost = ObjectReferenceHost.ofLocation(props.objectLocation)

        val value =
            (attributeNotation as? ScalarAttributeNotation)
                ?.value
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    val reference = ObjectReference.parse(it)
                    graphNotation.coalesce.locateOptional(reference, objectReferenceHost)
                }

        latestResolvedValue = value
        latestInitialized = true

        setState {
            this.value = value
            initialized = true
        }

        recomputeCandidates()
    }


    override fun onScriptState(scriptState: ScriptState) {
        latestScriptTree = scriptState.scriptTree
        recomputeCandidates()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun recomputeCandidates() {
        val graphNotation = latestGraphNotation
            ?: return
        val scriptTree = latestScriptTree
            ?: return

        if (props.objectLocation !in graphNotation.coalesce) {
            return
        }

        val candidates = ScriptNestingAnalysis.enclosingLoops(
            graphNotation,
            props.objectLocation.documentPath,
            scriptTree,
            props.objectLocation.objectPath)

        if (state.candidates != candidates) {
            setState {
                this.candidates = candidates
            }
        }

        // Pre-fill the innermost enclosing loop when this control step's target is still unset — makes a
        // freshly inserted ControlStep valid without manual selection. Runs once (a single default write via
        // the componentDidUpdate path, not a per-render echo). Guarded on the plain-field mirror, not `state`.
        if (! defaultApplied && latestInitialized && latestResolvedValue == null && candidates.isNotEmpty()) {
            defaultApplied = true
            latestResolvedValue = candidates.first()
            setState {
                this.value = candidates.first()
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
        val candidates = state.candidates
            ?: return

        val selectOptions: Array<SelectOption> = candidates
            .map { location ->
                val option: SelectOption = unsafeJso {
                    this.value = location.asString()
                    this.label = location.objectPath.name.value
                }
                option
            }
            .toTypedArray()

        val selectedValue = selectOptions.find { it.value == state.value?.asString() }

        muiAutocompleteField(
            label = formattedLabel(),
            options = selectOptions,
            selectedOption = selectedValue,
            onSelect = { onValueChange(ObjectLocation.parse(it.value)) },
            disableClearable = true)
    }


    private fun formattedLabel(): String {
        return CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
    }
}
