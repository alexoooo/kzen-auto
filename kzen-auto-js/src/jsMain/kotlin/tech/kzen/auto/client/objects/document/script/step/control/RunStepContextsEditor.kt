package tech.kzen.auto.client.objects.document.script.step.control

import emotion.react.css
import mui.material.IconButton
import mui.material.Size
import mui.system.sx
import js.objects.unsafeJso
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.common.objects.document.logic.context.ContextConventions
import tech.kzen.auto.common.objects.document.logic.context.ContextDescriptor
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
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
import tech.kzen.lib.platform.collect.persistentListOf
import tech.kzen.lib.platform.collect.toPersistentList
import tech.kzen.lib.platform.collect.toPersistentMap
import web.cssom.AlignItems
import web.cssom.Color
import web.cssom.Display
import web.cssom.em
import web.cssom.number
import web.cssom.px


//---------------------------------------------------------------------------------------------------------------------
/**
 * One `contexts:` entry as this editor holds it: the two RAW reference strings exactly as notated, plus what
 * each resolves to (null when it names nothing).
 *
 * The raw strings are the source of truth, NOT the resolved locations — the opposite of `RunStepArgumentsEditor`,
 * which keeps only `ObjectLocation`s and therefore silently drops an entry that resolves to nothing. Both sides
 * here are weak `by: Nominal` references whose dangling case is a validation FINDING (see
 * `LogicContextConventions.stepCallContexts` / `LogicContextAnalysis`), and this editor rewrites the whole map on
 * every edit — so resolving-then-reserializing would delete the very entry the finding is telling the user to
 * fix, on an unrelated row's edit. Keeping the raw text also preserves each surviving entry's exact spelling
 * (mirrors `ContextSignatureEditor.rawReferences`).
 */
data class RunStepContextEntry(
    val targetReference: String,
    val targetLocation: ObjectLocation?,
    val sourceReference: String,
    val sourceLocation: ObjectLocation?
)


//---------------------------------------------------------------------------------------------------------------------
external interface RunStepContextsEditorState: State {
    var initialized: Boolean
    var renaming: Boolean

    // The notated map, in notation order. Null until hydrated.
    var entries: PersistentList<RunStepContextEntry>?

    // Every `is: Context` in the graph, shared by both sides of every row. Recomputed when a picker OPENS
    // rather than per publish - see [refreshOptions].
    var options: Array<SelectOption>?

    // A row being composed. It is NOT in [entries] because a map entry is keyed by its target reference, so a
    // row with no target yet has no notation representation at all; it materializes the moment a target is
    // picked. [addingSourceKey] buffers a source picked first, so the two sides can be filled in either order.
    var adding: Boolean
    var addingSourceKey: String?
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Edits a RunStep's `contexts: {<callee's Context slot>: <a Context this caller holds>}` map — what this call
 * supplies to the document it runs, for that call only.
 *
 * Shaped after [RunStepArgumentsEditor], which edits the same `is: Map / of: [String, ObjectLocation] /
 * by: Nominal` notation: hydrate from the `MapAttributeNotation`, mutate state, and let `componentDidUpdate`
 * rewrite the WHOLE map with one `UpsertAttributeCommand`. The difference is that there the key is a free-text
 * parameter name of the callee's signature; here BOTH sides are references to `is: Context` objects, picked from
 * `ContextConventions.allContexts` exactly as [SelectContextEditor] does — including its reference-minting rule
 * (see [referenceOf]), which both the key and the value go through. That matters most for the KEY: a plain name
 * does not reach a user's `main.contexts/<Name>` declaration from another document, so a naively shortened
 * reference would silently dangle.
 *
 * Because the key IS a reference, retargeting a row rewrites the map key — which is why the whole map is
 * rewritten in place (row order preserved) rather than one key upserted.
 */
@Suppress("unused")
class RunStepContextsEditor(
    props: AttributeEditorProps
):
    ObjectScopedComponent<AttributeEditorProps, RunStepContextsEditorState>(props),
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
            RunStepContextsEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // The callee's slot, then what this caller puts in it. The arrow points the way the VALUE travels, so it
        // reads left-to-right as "this slot is filled from that declaration".
        private const val targetLabel = "Requires"
        private const val sourceLabel = "Supplied by"

        private val mutedColour = Color("rgba(0, 0, 0, 0.55)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Hydrated synchronously (as SelectContextEditor does), so the rows are painted on the first render and there
    // is no undefined -> loaded transition for componentDidUpdate to echo back as a no-op write.
    override fun RunStepContextsEditorState.init(props: AttributeEditorProps) {
        val graphNotation = props.clientStateGlobal.current()?.graphStructure()?.graphNotation

        initialized = graphNotation != null
        renaming = false

        entries = graphNotation?.let { readEntries(it) }
        options = graphNotation?.let { contextOptions(it) }

        adding = false
        addingSourceKey = null
    }


    //-----------------------------------------------------------------------------------------------------------------
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
    }


    override fun componentDidUpdate(
        prevProps: AttributeEditorProps,
        prevState: RunStepContextsEditorState,
        snapshot: Any
    ) {
        if (state.entries != prevState.entries) {
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


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val newEntries = readEntries(clientState.graphStructure().graphNotation)

        // Value compare before setState: the list is freshly allocated every publish, so an unconditional
        // setState would defeat RPureComponent's shallow bail-out AND make componentDidUpdate above write the
        // notation straight back out on any unrelated edit in the document.
        if (state.initialized && newEntries == state.entries) {
            return
        }

        setState {
            initialized = true
            entries = newEntries
        }
    }


    private fun readEntries(graphNotation: GraphNotation): PersistentList<RunStepContextEntry> {
        // NB: the nullable AttributePath overload of firstAttribute - the AttributeName one throws when the
        // attribute is absent, and this editor can be pointed at an attribute a subtype does not restate.
        if (props.objectLocation !in graphNotation.coalesce) {
            return persistentListOf()
        }

        val attributeNotation = graphNotation.firstAttribute(
            props.objectLocation, AttributePath.ofName(props.attributeName)) as? MapAttributeNotation
            ?: return persistentListOf()

        return attributeNotation
            .map
            .map { (segment, valueNotation) ->
                val targetReference = segment.asKey()
                val sourceReference = (valueNotation as? ScalarAttributeNotation)?.value ?: ""

                RunStepContextEntry(
                    targetReference,
                    resolveOrNull(graphNotation, targetReference),
                    sourceReference,
                    resolveOrNull(graphNotation, sourceReference))
            }
            .toPersistentList()
    }


    private fun resolveOrNull(graphNotation: GraphNotation, reference: String): ObjectLocation? {
        return ContextConventions
            .resolveOrNull(graphNotation, reference, props.objectLocation)
            ?.location
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        val entries = state.entries
            ?: return

        when (event) {
            is RenamedObjectRefactorEvent -> {
                val oldLocation = event.renamedObject.objectLocation
                val newLocation = event.renamedObject.newObjectLocation()

                // NB: the event's own notation, not clientStateGlobal.current() - it IS the post-rename graph,
                // which is what the new reference has to be minted against (mirrors SelectContextEditor).
                val graphNotation = graphDefinition.graphStructure.graphNotation
                val newReference = referenceOf(graphNotation, newLocation)

                var targetRenamed = false
                val newEntries = entries
                    .map { entry ->
                        var renamedEntry = entry
                        if (referredTo(entry.targetReference, entry.targetLocation, oldLocation)) {
                            targetRenamed = true
                            renamedEntry = renamedEntry.copy(
                                targetReference = newReference, targetLocation = newLocation)
                        }
                        if (referredTo(entry.sourceReference, entry.sourceLocation, oldLocation)) {
                            renamedEntry = renamedEntry.copy(
                                sourceReference = newReference, sourceLocation = newLocation)
                        }
                        renamedEntry
                    }
                    .toPersistentList()

                if (newEntries == entries) {
                    return
                }

                setState {
                    this.entries = newEntries

                    // A renamed VALUE was already rewritten in notation by the refactor, so adopting it must not
                    // write. A renamed KEY was NOT - kzen-lib's refactor rewrites reference VALUES, and a map
                    // key is not one - so that case deliberately falls through to componentDidUpdate's write,
                    // which is the only thing that can repair the key.
                    renaming = ! targetRenamed
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
    /**
     * Did [reference] name [renamed] before it was renamed? [resolved] is what the reference resolves to NOW.
     *
     * A plain `resolved == renamed` test — which is all [RunStepArgumentsEditor] needs — answers only half of
     * this, and never the half that matters. `ClientStateGlobal` observes the DirectGraphStore and is subscribed
     * at boot, while an attribute editor observes the MirroredGraphStore, and `MirroredGraphStore.apply` runs the
     * local apply (which notifies `ClientStateGlobal`, which publishes) BEFORE its own `publishSuccess` — so
     * [onClientState] has already re-read post-rename notation by the time the rename event arrives here. The
     * VALUE side survives that (the refactor rewrote it, so it resolves to the new location and there is nothing
     * left to adopt); the KEY side does not (a map key is not a reference the refactor rewrites, so it is sitting
     * there dangling with no location left to match on).
     *
     * Hence the second clause: a reference that resolves to NOTHING is matched against the two canonical
     * spellings of the renamed object — the exact inverse of what [referenceOf] mints, not a new resolution rule.
     * Because it requires the reference to be dangling, it cannot steal a live one that merely shares a bare
     * name; the same-name-in-another-document case still resolves and is left alone.
     */
    private fun referredTo(reference: String, resolved: ObjectLocation?, renamed: ObjectLocation): Boolean {
        if (resolved != null) {
            return resolved == renamed
        }
        if (reference.isEmpty()) {
            return false
        }

        val qualified = renamed.toReference()
        return reference == qualified.asString() ||
                reference == qualified.crop(retainPath = false).asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun graphNotation(): GraphNotation? {
        return props.clientStateGlobal.current()?.graphStructure()?.graphNotation
    }


    /**
     * Prefer the document-relative object path (`main.contexts/Browser A`) — terser, and what a hand-written
     * entry looks like — but only when it actually resolves back to THIS Context from this step. Verbatim from
     * `SelectContextEditor.wireValue`, and applied to the KEY as well as the value: the key is a reference in the
     * same namespace (both sides resolve against this step — see `LogicContextConventions.stepCallContexts`), and
     * a bare name does not reach a declaration nested under another document's `main`, so shortening without
     * checking would write a silently dangling entry rather than an error.
     */
    private fun referenceOf(graphNotation: GraphNotation?, location: ObjectLocation): String {
        val qualified = location.toReference()
        if (graphNotation == null) {
            return qualified.asString()
        }

        val objectPath = qualified.crop(retainPath = false).asString()
        val resolved = resolveOrNull(graphNotation, objectPath)

        return if (resolved == location) objectPath else qualified.asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onTargetChange(index: Int, optionKey: String) {
        val entries = state.entries
            ?: return

        val location = ObjectLocation.parse(optionKey)

        setState {
            this.entries = entries.set(index, entries[index].copy(
                targetReference = referenceOf(graphNotation(), location),
                targetLocation = location))
        }
    }


    private fun onSourceChange(index: Int, optionKey: String) {
        val entries = state.entries
            ?: return

        val location = ObjectLocation.parse(optionKey)

        setState {
            this.entries = entries.set(index, entries[index].copy(
                sourceReference = referenceOf(graphNotation(), location),
                sourceLocation = location))
        }
    }


    private fun onRemove(index: Int) {
        val entries = state.entries
            ?: return

        setState {
            this.entries = entries.removeAt(index)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onStartAdding() {
        refreshOptions()

        setState {
            adding = true
            addingSourceKey = null
        }
    }


    private fun onCancelAdding() {
        setState {
            adding = false
            addingSourceKey = null
        }
    }


    // Buffered rather than written: there is no map key to write it under until a target is picked.
    private fun onAddingSourceChange(optionKey: String) {
        setState {
            addingSourceKey = optionKey
        }
    }


    // Picking the target is what turns the composed row into a real entry - with whatever source was buffered,
    // possibly none. A half-written entry is deliberately allowed to reach notation: the server reader skips it
    // (see LogicContextConventions.stepCallContexts) rather than reporting an error the author is mid-way
    // through fixing.
    private fun onAddingTargetChange(optionKey: String) {
        val entries = state.entries
            ?: return

        val graphNotation = graphNotation()
        val targetLocation = ObjectLocation.parse(optionKey)
        val sourceLocation = state.addingSourceKey?.let { ObjectLocation.parse(it) }

        val entry = RunStepContextEntry(
            referenceOf(graphNotation, targetLocation),
            targetLocation,
            sourceLocation?.let { referenceOf(graphNotation, it) } ?: "",
            sourceLocation)

        setState {
            this.entries = entries.add(entry)
            adding = false
            addingSourceKey = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun editAttributeCommandAsync() {
        async {
            editAttributeCommand()
        }
    }


    private suspend fun editAttributeCommand() {
        val entries = state.entries
            ?: return

        // LinkedHashMap, so the notated row order survives a retarget (which rewrites a key rather than editing
        // a value in place). An entry with no target has no key to live under and is dropped - it can only come
        // from hand-written notation, since the composed row is held outside [entries] until it has one.
        val map = LinkedHashMap<AttributeSegment, AttributeNotation>()
        for (entry in entries) {
            if (entry.targetReference.isEmpty()) {
                continue
            }
            map[AttributeSegment.ofKey(entry.targetReference)] = ScalarAttributeNotation(entry.sourceReference)
        }

        props.mirroredGraphStore.apply(UpsertAttributeCommand(
            props.objectLocation,
            props.attributeName,
            MapAttributeNotation(map.toPersistentMap())))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun contextOptions(graphNotation: GraphNotation): Array<SelectOption> {
        return ContextConventions
            .allContexts(graphNotation)
            .sortedBy { it.label() }
            .map { descriptor ->
                val option: SelectOption = unsafeJso {
                    value = descriptor.location.asString()
                    label = descriptor.label()
                    detail = optionDetailOf(descriptor)
                    detailTitle = descriptor.type.className.asString()
                }
                option
            }
            .toTypedArray()
    }


    // The value contract and the description are what tell two similarly-named declarations apart; the row shows
    // the type unqualified, with the qualified class on hover.
    private fun optionDetailOf(descriptor: ContextDescriptor): String? {
        val parts = listOfNotNull(
            descriptor.typeLabel().ifEmpty { null },
            descriptor.description.ifEmpty { null })
        return parts.joinToString(" — ").ifEmpty { null }
    }


    // On picker-open rather than per publish: ContextConventions.allContexts walks the inheritance chain of every
    // object in the coalesced graph, and a committed change yields a fresh GraphNotation whose chain cache starts
    // cold - so an on-every-event refresh would repeat that graph-wide walk in every mounted editor for each
    // debounced keystroke elsewhere in the document.
    private fun refreshOptions() {
        val graphNotation = graphNotation()
            ?: return

        val options = contextOptions(graphNotation)

        // Content compare: SelectOption is an external interface and Array equality is by reference, so a
        // freshly rebuilt but identical list would otherwise defeat RPureComponent's shallow bail-out.
        val current = state.options
        if (current != null && current.size == options.size &&
                options.indices.all {
                    current[it].value == options[it].value && current[it].label == options[it].label
                }
        ) {
            return
        }

        setState {
            this.options = options
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val entries = state.entries
            ?: return
        val options = state.options
            ?: return

        for ((index, entry) in entries.withIndex()) {
            div {
                key = Key(entry.targetReference.ifEmpty { "#$index" })
                renderEntry(index, entry, options)
            }
        }

        if (state.adding) {
            renderAddedEntry(options)
        }
        else {
            renderAddButton()
        }
    }


    private fun ChildrenBuilder.renderEntry(
        index: Int,
        entry: RunStepContextEntry,
        options: Array<SelectOption>
    ) {
        renderRow(
            targetOptions = targetOptions(options, entry.targetLocation),
            targetSelected = options.find { it.value == entry.targetLocation?.asString() },
            targetError = entry.targetLocation == null && entry.targetReference.isNotEmpty(),
            onTargetSelect = { onTargetChange(index, it) },
            sourceOptions = options,
            sourceSelected = options.find { it.value == entry.sourceLocation?.asString() },
            sourceError = entry.sourceLocation == null && entry.sourceReference.isNotEmpty(),
            onSourceSelect = { onSourceChange(index, it) },
            actionTitle = "Remove",
            actionIcon = "material-symbols:do-not-disturb-on-outline",
            onAction = { onRemove(index) })

        // A dangling reference leaves its field EMPTY (SelectContextEditor's rule - never invent an option for
        // it), which on its own would hide what the analysis is complaining about. So say what is actually in
        // notation, in place. This is the surface for every dangling entry this editor was not mounted to
        // repair: [referredTo] follows a rename only while the step's body is open.
        renderUnresolved(entry)
    }


    // The composed row. Its target picker offers what no existing row already targets, so a pick can never
    // collide with an existing map key and silently replace it.
    private fun ChildrenBuilder.renderAddedEntry(options: Array<SelectOption>) {
        renderRow(
            targetOptions = targetOptions(options, null),
            targetSelected = null,
            targetError = false,
            onTargetSelect = { onAddingTargetChange(it) },
            sourceOptions = options,
            sourceSelected = options.find { it.value == state.addingSourceKey },
            sourceError = false,
            onSourceSelect = { onAddingSourceChange(it) },
            actionTitle = "Cancel",
            actionIcon = "material-symbols:cancel",
            onAction = { onCancelAdding() })
    }


    private fun ChildrenBuilder.renderRow(
        targetOptions: Array<SelectOption>,
        targetSelected: SelectOption?,
        targetError: Boolean,
        onTargetSelect: (String) -> Unit,
        sourceOptions: Array<SelectOption>,
        sourceSelected: SelectOption?,
        sourceError: Boolean,
        onSourceSelect: (String) -> Unit,
        actionTitle: String,
        actionIcon: String,
        onAction: () -> Unit
    ) {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
            }

            // Both selects grow; minWidth 0 lets them shrink so the arrow and the button never force overflow.
            div {
                css {
                    flexGrow = number(1.0)
                    minWidth = 0.px
                }

                muiAutocompleteField(
                    label = targetLabel,
                    options = targetOptions,
                    selectedOption = targetSelected,
                    onSelect = { onTargetSelect(it.value) },
                    disableClearable = true,
                    error = targetError,
                    onOpen = ::refreshOptions)
            }

            span {
                css {
                    marginLeft = 0.5.em
                    marginRight = 0.5.em
                    color = mutedColour
                }
                +"←"
            }

            div {
                css {
                    flexGrow = number(1.0)
                    minWidth = 0.px
                }

                muiAutocompleteField(
                    label = sourceLabel,
                    options = sourceOptions,
                    selectedOption = sourceSelected,
                    onSelect = { onSourceSelect(it.value) },
                    disableClearable = true,
                    error = sourceError,
                    onOpen = ::refreshOptions)
            }

            IconButton {
                sx {
                    marginLeft = 0.25.em
                }
                title = actionTitle
                size = Size.small

                onClick = {
                    onAction()
                }

                icon(actionIcon) {}
            }
        }
    }


    private fun ChildrenBuilder.renderUnresolved(entry: RunStepContextEntry) {
        val unresolved = listOfNotNull(
            entry.targetReference.takeIf { it.isNotEmpty() && entry.targetLocation == null },
            entry.sourceReference.takeIf { it.isNotEmpty() && entry.sourceLocation == null })

        if (unresolved.isEmpty()) {
            return
        }

        div {
            css {
                fontSize = 0.8.em
                color = mutedColour
            }
            +"Unresolved: ${unresolved.joinToString(", ")}"
        }
    }


    private fun ChildrenBuilder.renderAddButton() {
        IconButton {
            title = "Supply a context to the document this step runs"
            size = Size.small

            onClick = {
                onStartAdding()
            }

            icon("material-symbols:add-circle-outline") {}
        }
    }


    // Every Context except the ones another row already targets: two rows sharing a target are two entries with
    // one map key, and the whole-map write would silently keep only the last. [own] is retained so THIS row's
    // current selection still has an option to display.
    private fun targetOptions(options: Array<SelectOption>, own: ObjectLocation?): Array<SelectOption> {
        val taken = (state.entries ?: persistentListOf())
            .mapNotNull { it.targetLocation }
            .filter { it != own }
            .map { it.asString() }
            .toSet()

        if (taken.isEmpty()) {
            return options
        }

        return options.filter { it.value !in taken }.toTypedArray()
    }
}
