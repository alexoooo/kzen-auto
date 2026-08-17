package tech.kzen.auto.client.objects.document.script.display.edit


import js.objects.unsafeJso
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.AttributeCommitter
import tech.kzen.auto.client.objects.document.common.edit.documentEditActivity
import tech.kzen.auto.client.objects.document.script.display.target.TargetTypeDisplay
import tech.kzen.auto.client.objects.document.script.display.target.TargetValueEditorContext
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.common.objects.document.target.TargetSpecDefiner
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedDocumentRefactorEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.platform.collect.toPersistentMap


//---------------------------------------------------------------------------------------------------------------------
external interface TargetSpecEditorProps: AttributeEditorProps {
    var navigationGlobal: NavigationGlobal
    var targetTypes: List<TargetTypeDisplay>
}


external interface TargetSpecEditorState: State {
    var typeName: String?
    var value: String?

    // Set by a committed (non-debounced) value change: componentDidUpdate writes immediately
    var immediateWrite: Boolean

    var renaming: Boolean

    var clientState: ClientState?

    // False until the first onClientState hydration; gates componentDidUpdate so the undefined→loaded
    // transition isn't echoed back to the notation as a spurious UpsertAttributeCommand on mount/expand.
    var initialized: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Type-agnostic host of the `target:` attribute editor: the Target Type dropdown lists the
 * registered [TargetTypeDisplay]s, and the selected type's fragment renders the value row —
 * this host owns only the notation write machinery (see the fragment for the per-type UI).
 */
@Suppress("unused")
class TargetSpecEditor(
    props: TargetSpecEditorProps
):
    ObjectScopedComponent<TargetSpecEditorProps, TargetSpecEditorState>(props),
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val targetTypes: List<TargetTypeDisplay>,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val navigationGlobal: NavigationGlobal
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            TargetSpecEditor::class.react {
                targetTypes = this@Wrapper.targetTypes
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                navigationGlobal = this@Wrapper.navigationGlobal
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The value row belongs to the per-type fragment, so this host has no field of its own to turn red — a failed
    // write surfaces through the global banner only.
    // NB: `this.props` - see the shadowing note in TextAttributeEditor.
    private val committer = AttributeCommitter(
        graphStore = { this.props.mirroredGraphStore },
        objectLocation = { this.props.objectLocation },
        attributePath = { AttributePath.ofName(this.props.attributeName) },
        pendingNotation = { pendingNotation() },
        editActivity = { documentEditActivity() })


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    override fun TargetSpecEditorState.init(props: TargetSpecEditorProps) {
        initialized = false
    }


    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphStructure().graphNotation

        val attributeNotation = graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)
                as? MapAttributeNotation
            ?: return

        val typeName = attributeNotation
            .get(TargetSpecDefiner.typeKey)
            ?.asString()
            ?: return

        setState {
            this.typeName = typeName
            value = attributeNotation.get(TargetSpecDefiner.valueKey)?.asString()
            initialized = true

            immediateWrite = false
            renaming = false

            this.clientState = clientState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun typeDisplay(typeName: String?): TargetTypeDisplay? {
        return props.targetTypes.find { it.typeName == typeName }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
        prevProps: TargetSpecEditorProps,
        prevState: TargetSpecEditorState,
        snapshot: Any
    ) {
        if (!prevState.initialized) {
            // First hydration (undefined → loaded) came from onClientState reading the notation; don't
            // echo it straight back as a write. This is what made expanding a target step issue a no-op
            // UpsertAttributeCommand that re-rendered the whole branch + slots. Genuine user edits below
            // run only once we're past hydration.
            return
        }

        if (state.typeName != prevState.typeName) {
            // Types with a value would fail the document definition (blocking the run ribbon) if
            // {type} upserted alone — hold the write until the value edit carries both segments.
            if (typeDisplay(state.typeName)?.hasValue == false) {
                commitNowAsync()
            }
        }
        else if (state.value != prevState.value) {
            if (state.renaming) {
                setState {
                    renaming = false
                }
            }
            else if (state.immediateWrite) {
                setState {
                    immediateWrite = false
                }
                commitNowAsync()
            }
            else {
                committer.schedule()
            }
        }
    }


    private var mounted = false


    override fun componentDidMount() {
        mounted = true
        super.componentDidMount()
        async {
            // Unobserve runs synchronously on unmount, so registering after it would leak this observer.
            if (mounted) {
                props.mirroredGraphStore.observe(this)
            }
        }
    }


    override fun componentWillUnmount() {
        mounted = false
        props.mirroredGraphStore.unobserve(this)
        super.componentWillUnmount()
        committer.flush()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        when (event) {
            is RenamedDocumentRefactorEvent -> {
                // A reference-valued target follows its renamed document (the refactor already
                // rewrote the notation; `renaming` suppresses the echo write). A free-text value
                // that doesn't parse as a reference is simply not affected.
                val reference = state.value?.let {
                    runCatching { ObjectReference.parse(it) }.getOrNull()
                }

                if (reference?.path == event.removedUnderOldName.documentPath) {
                    val newReference = ObjectReference(
                        reference.name, reference.nesting, event.createdWithNewName.destination)

                    setState {
                        value = newReference.asString()
                        renaming = true
                    }
                }
            }

            else -> {}
        }
    }


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {}


    //-----------------------------------------------------------------------------------------------------------------
    // Safe to read the pending value at commit time: both callers run from componentDidUpdate, so the state
    // change that triggered the write is already committed.
    private fun commitNowAsync() {
        async {
            committer.commitNow()
        }
    }


    private fun pendingNotation(): AttributeNotation? {
        val typeName = state.typeName
            ?: return null

        val attributeMap = mutableMapOf<AttributeSegment, AttributeNotation>()

        attributeMap[TargetSpecDefiner.typeSegment] =
                ScalarAttributeNotation(typeName)

        state.value?.let {
            attributeMap[TargetSpecDefiner.valueSegment] =
                    ScalarAttributeNotation(it)
        }

        // Preserve keys this editor doesn't own (e.g. `policy:`) across a rewrite. The stored clientState can
        // predate a rename of our own host (onClientState skips a broadcast carrying a stale objectLocation, so
        // it never refreshes), and reading it would then throw. Bail rather than commit — writing a map we
        // couldn't read back would silently drop `policy:`, turning a strict target loose.
        val graphNotation = state.clientState
            ?.graphStructure()
            ?.graphNotation
            ?: return null

        if (props.objectLocation !in graphNotation.coalesce) {
            return null
        }

        val currentNotation = graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)
            as? MapAttributeNotation

        currentNotation?.map?.forEach { (segment, notation) ->
            if (segment != TargetSpecDefiner.typeSegment &&
                    segment != TargetSpecDefiner.valueSegment) {
                attributeMap[segment] = notation
            }
        }

        return MapAttributeNotation(attributeMap.toPersistentMap())
    }


    private fun onTypeChange(newTypeName: String) {
        // A pending debounced text write would fire after the value fields are cleared, emitting
        // a value-less target map; the pre-switch text (if any) was already committed by onBlur.
        committer.cancel()

        setState {
            typeName = newTypeName
            value = null
        }
    }


    private fun onValueEdit(newValue: String) {
        setState {
            value = newValue
        }
    }


    private fun onValueChange(newValue: String) {
        setState {
            value = newValue
            immediateWrite = true
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        renderSelectType()

        val display = typeDisplay(state.typeName)
            ?: return

        val context = TargetValueEditorContext(
            value = state.value,
            objectLocation = props.objectLocation,
            clientState = state.clientState,
            navigationGlobal = props.navigationGlobal,
            onValueChange = ::onValueChange,
            onValueEdit = ::onValueEdit,
            onEditCommit = { committer.flush() })

        with(display) {
            renderValueEditor(context)
        }
    }


    private fun ChildrenBuilder.renderSelectType() {
        val typeName = state.typeName
            ?: return

        val typeOptions = props.targetTypes
            .map { type ->
                val option: SelectOption = unsafeJso {
                    value = type.typeName
                    label = type.editorLabel
                }
                option
            }
            .toTypedArray()

        div {
            muiAutocompleteField(
                label = "Target Type",
                options = typeOptions,
                selectedOption = typeOptions.find { it.value == typeName },
                onSelect = { onTypeChange(it.value) },
                disableClearable = true)
        }
    }
}
