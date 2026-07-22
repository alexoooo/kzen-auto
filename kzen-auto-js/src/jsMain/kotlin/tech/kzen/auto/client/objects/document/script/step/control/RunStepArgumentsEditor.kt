package tech.kzen.auto.client.objects.document.script.step.control

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Chip
import mui.material.ChipVariant
import mui.material.IconButton
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.Key
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.signature.LogicTypeOptions
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreKey
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.common.objects.document.flow.FlowConventions
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.job.JobSignatureCapability
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.RunStepInstructions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedObjectRefactorEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.platform.collect.PersistentList
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.toPersistentList
import tech.kzen.lib.platform.collect.toPersistentMap
import web.cssom.AlignItems
import web.cssom.Display
import web.cssom.em
import web.cssom.number
import web.cssom.px


//---------------------------------------------------------------------------------------------------------------------
external interface RunStepArgumentsEditorState: State {
    var initialized: Boolean
    var renaming: Boolean

    var values: PersistentMap<String, ObjectLocation>?
    var predecessors: PersistentList<ObjectLocation>?
    var parameterNames: PersistentList<String>?
    var parameterTypes: PersistentMap<String, String>?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class RunStepArgumentsEditor(
    props: AttributeEditorProps
):
    RPureComponent<AttributeEditorProps, RunStepArgumentsEditorState>(props),
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
            RunStepArgumentsEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val typeAttributePath = AttributePath.ofName(AttributeName("type"))
        private const val classKey = "class"
        private const val nullableKey = "nullable"
        private const val defaultClassName = "kotlin.Any"
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RunStepArgumentsEditorState.init(props: AttributeEditorProps) {
        initialized = false
        renaming = false

        values = null
        predecessors = null
        parameterNames = null
        parameterTypes = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidUpdate(
        prevProps: AttributeEditorProps,
        prevState: RunStepArgumentsEditorState,
        snapshot: Any
    ) {
        if (state.values != prevState.values) {
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

        val objectReferenceHost = ObjectReferenceHost.ofLocation(props.objectLocation)

        val instructionsObjectLocation = RunStepInstructions.instructionsLocation(
            graphNotation, props.objectLocation)

        // Enumerate the callee Logic's parameter names, dispatching on the callee's document type (assuming the
        // Script shape found nothing for a Flow / Job and rendered no argument rows):
        //  - a Script keeps them as nested ParameterBinding objects under its `parameters` branch (named by
        //    object name), each carrying a declared `type` (a MapAttributeNotation of {class, generics, nullable},
        //    the same shape LogicSignatureEditor edits) that we format into a badge label;
        //  - a Flow keeps them as FlowInput vertices in its `vertices` list (each carrying a `parameter` name),
        //    with no typed signature in this shape, so its parameters get no badge;
        //  - a Job declares them as ParameterSource Workers (JobSignatureCapability derives the signature — the
        //    same notation-only derivation JobLogicCompiler reads server-side, so the two can't drift), typed by
        //    each source's output-port `of:` (untyped ports badge as "Any", Script parity).
        val instructionsParameters: List<String>?
        val newParameterTypes = mutableMapOf<String, String>()
        if (instructionsObjectLocation != null) {
            val documentNotation = graphNotation.documents[instructionsObjectLocation.documentPath]
            if (documentNotation != null && FlowConventions.isFlow(documentNotation)) {
                instructionsParameters = FlowConventions.inputParameterNames(
                    graphNotation, instructionsObjectLocation)
            }
            else if (documentNotation != null && JobConventions.isJob(documentNotation)) {
                val signature = JobSignatureCapability.signature(
                    clientState.graphStructure(), instructionsObjectLocation)
                instructionsParameters = signature.inputs.components.map { it.name.value }
                for (component in signature.inputs.components) {
                    val metadata = component.type.metadata
                    newParameterTypes[component.name.value] =
                        LogicTypeOptions.simpleLabel(metadata.className.asString(), metadata.nullable)
                }
            }
            else {
                val parameterPaths = documentNotation
                    ?.directNestedObjectPaths(
                        instructionsObjectLocation.objectPath,
                        ScriptConventions.parametersAttributeName)
                instructionsParameters = parameterPaths?.map { it.name.value }

                parameterPaths?.forEach { parameterPath ->
                    val parameterLocation = instructionsObjectLocation
                        .documentPath.toObjectLocation(parameterPath)
                    val typeNotation = graphNotation
                        .firstAttribute(parameterLocation, typeAttributePath) as? MapAttributeNotation
                    val className = typeNotation?.get(classKey)?.asString() ?: defaultClassName
                    val nullable = typeNotation?.get(nullableKey)?.asString()?.toBoolean() ?: false
                    newParameterTypes[parameterPath.name.value] =
                        LogicTypeOptions.simpleLabel(className, nullable)
                }
            }
        }
        else {
            instructionsParameters = null
        }

        val attributeNotation = graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)

        val attributeMap: Map<String, String> =
            (attributeNotation as? MapAttributeNotation)
            ?.map
            ?.map { it.key.asKey() to (it.value as ScalarAttributeNotation).value }
            ?.toMap()
            ?: mapOf()

        val values = mutableMapOf<String, ObjectLocation>()
        for (e in attributeMap) {
            val reference = ObjectReference.parse(e.value)

            val value: ObjectLocation? =
                graphNotation.coalesce.locateOptional(reference, objectReferenceHost)

            if (value != null) {
                values[e.key] = value
            }
        }

        setState {
            initialized = true

            this.values = values.toPersistentMap()
            parameterNames = instructionsParameters?.toPersistentList()
            parameterTypes = newParameterTypes.toPersistentMap()
        }
    }


    // The bindable values offered per parameter come from the ScriptStore's lexical step tree, not a
    // flat "every step in the document" list: a value bound to a callee parameter must be a step/binding
    // actually in scope at this RunStep — prior steps up the tree plus the enclosing ForEach items /
    // Script parameters — NOT a step buried inside a sibling branch (standard block scoping). Mirrors
    // SelectStepEditor.
    override fun onScriptState(scriptState: ScriptState) {
        val scriptTree = scriptState.scriptTree
        val targetPath = props.objectLocation.objectPath

        val candidatePaths = scriptTree.inScopeReferencePaths(targetPath)
        val predecessors = candidatePaths
            .map { props.objectLocation.documentPath.toObjectLocation(it) }
            .toPersistentList()

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
        val values = state.values
            ?: return

        when (event) {
            is RenamedObjectRefactorEvent -> {
                val entryList = values.entries.toList()
                val renamedEntry = entryList.find { it.value == event.renamedObject.objectLocation }
                    ?: return

                setState {
                    this.values = values.put(
                        renamedEntry.key, event.renamedObject.newObjectLocation())
                    renaming = true
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
    private fun onRemove(parameterName: String) {
        val values = state.values
            ?: return

        setState {
            this.values = values.remove(parameterName)
        }
    }


    private fun onValueChange(parameterName: String, value: ObjectLocation) {
        val values = state.values
            ?: return

        setState {
            this.values = values.put(parameterName, value)
        }
    }


    private fun editAttributeCommandAsync() {
        async {
            editAttributeCommand()
        }
    }


    private suspend fun editAttributeCommand() {
        val values = state.values
            ?: return

        val localReferences: PersistentMap<AttributeSegment, AttributeNotation> =
            values
            .entries
            .map {
                val localReference = it.value.toReference().crop(retainPath = false)
                AttributeSegment.ofKey(it.key) to
                        ScalarAttributeNotation(localReference.asString())
            }
            .toPersistentMap()

        props.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                props.attributeName,
                MapAttributeNotation(localReferences)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val values = state.values ?: return
        val predecessors = state.predecessors ?: return
        val parameterNames = state.parameterNames ?: return
        val parameterTypes = state.parameterTypes

        val selectOptions: Array<SelectOption> = predecessors
            .map { location ->
                val option: SelectOption = unsafeJso {
                    this.value = location.asString()
                    this.label = location.objectPath.name.value
                }
                option
            }
            .toTypedArray()

        for (parameterName in parameterNames) {
            div {
                key = Key(parameterName)
                renderParameter(parameterName, parameterTypes?.get(parameterName), selectOptions, values)
            }
        }

        val unusedParameters = values.keys.minus(parameterNames)
        for (unusedParameter in unusedParameters) {
            div {
                key = Key(unusedParameter)
                renderUnusedParameter(unusedParameter, selectOptions, values)
            }
        }
    }


    private fun ChildrenBuilder.renderParameter(
        parameterName: String,
        typeLabel: String?,
        selectOptions: Array<SelectOption>,
        values: PersistentMap<String, ObjectLocation>
    ) {
        val selectedValue = values[parameterName]
        val selectedOption = selectOptions.find { it.value == selectedValue?.asString() }

        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
            }

            // The select grows; minWidth 0 lets it shrink so the type badge never forces overflow.
            div {
                css {
                    flexGrow = number(1.0)
                    minWidth = 0.px
                }

                muiAutocompleteField(
                    label = parameterName,
                    options = selectOptions,
                    selectedOption = selectedOption,
                    onSelect = { onValueChange(parameterName, ObjectLocation.parse(it.value)) },
                    disableClearable = true)
            }

            if (typeLabel != null) {
                Chip {
                    sx {
                        marginLeft = 0.5.em
                        flexShrink = number(0.0)
                    }
                    size = Size.small
                    label = ReactNode(typeLabel)
                    variant = ChipVariant.outlined
                }
            }
        }
    }


    private fun ChildrenBuilder.renderUnusedParameter(
        parameterName: String,
        selectOptions: Array<SelectOption>,
        values: PersistentMap<String, ObjectLocation>
    ) {
        val selectedValue = values[parameterName]
        val selectedOption = selectOptions.find { it.value == selectedValue?.asString() }

        +"Unused parameter: $parameterName - ${selectedOption?.label}"

        IconButton {
            sx {
                marginLeft = 0.25.em
            }
            title = "Remove"

            onClick = {
                onRemove(parameterName)
            }

            icon("material-symbols:do-not-disturb-on-outline") {
                style = unsafeJso {
                    fontSize = 1.5.em
                }
            }
        }
    }
}
