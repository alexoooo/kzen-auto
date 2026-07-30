package tech.kzen.auto.client.objects.document.common.signature

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Chip
import mui.material.ChipVariant
import mui.material.IconButton
import mui.material.Size
import mui.material.Tooltip
import mui.system.sx
import react.ChildrenBuilder
import react.Fragment
import react.Props
import react.ReactNode
import react.State
import react.create
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.StageErrorIndicator
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedProps
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.logic.context.ContextConventions
import tech.kzen.auto.common.objects.document.logic.context.ContextDescriptor
import tech.kzen.auto.common.objects.document.logic.context.LogicContextAnalysis
import tech.kzen.auto.common.objects.document.logic.context.LogicContextConventions
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.platform.collect.toPersistentList
import tech.kzen.lib.platform.collect.toPersistentMap
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ContextSignatureEditorProps: ObjectScopedProps {
    var mirroredGraphStore: MirroredGraphStore
}


external interface ContextSignatureEditorState: State {
    // The document's own `context.exports` / `context.requires`, resolved. Null until the first client state.
    var exports: List<ContextDescriptor>?
    var requires: List<ContextDescriptor>?

    // The two document-level warnings, computed locally from notation: which declared exports nothing in the
    // document can back, and whether a retired `context.slots` declaration is still present. Document-level
    // warnings have no other rendering surface — the validation channel carries document-level ERRORS only.
    var unbackedExports: List<ContextDescriptor>?
    var legacySlots: Boolean

    // Every `is: Context` in the graph — the picker's options. Populated ON DEMAND (when the picker opens)
    // rather than per publish: ContextConventions.allContexts walks the inheritance chain of every object in
    // the coalesced graph, which is far too much work to repeat on each ClientState broadcast for a list the
    // user sees only while adding.
    var pickerOptions: List<ContextDescriptor>?

    // The collapsed chip row expands into the two-field picker when true.
    var adding: Boolean
    // Which role the picker will add into: true = a Context this document exports, false = one a caller supplies.
    var addingExports: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Edits a Logic document's run-scoped **Context** signature: the `context.exports` it offers upward (a step
 * provide of one climbs past this document's frame to its caller; anything not listed is private to this
 * document and disposed at its settle) and the `context.requires` it asserts a CALLER has already provided.
 * `context` is data declared `by: Nominal` (weak references, never constructor-injected) and has no generic
 * attribute editor, so this reads and writes it directly.
 *
 * Floated at the top-right of the stage, third in the stack beneath Parameters and Result, and therefore with
 * ZERO flow footprint: a document that declares no Context costs no vertical space, and — the load-bearing
 * part — the component is emitted UNCONDITIONALLY from `ScriptController.renderSignature`, so the step
 * subtree's child index never shifts (see the comment on the document-level error slot there).
 *
 * Writes upsert the WHOLE `context` map (mirroring [ResultSignatureEditor]'s whole-`results` upsert) rather
 * than inserting into one key. That is necessary, not merely simpler: `context` is inherited from the `Script`
 * archetype (`context: {}`) and does not exist on a document's own `main` until the first edit, so a
 * list-insert command would have no local attribute to land in. Entries are written as LIST items, matching
 * the archetype's `is: Map, of: [String, {is: List, of: ObjectLocation}]` meta declaration; any `context` key
 * this editor does not recognize is carried through verbatim, so a whole-map upsert never destroys one.
 */
class ContextSignatureEditor:
    ObjectScopedComponent<ContextSignatureEditorProps, ContextSignatureEditorState>()
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val exportsRoleValue = "exports"
        private const val requiresRoleValue = "requires"

        // Same skin language as the step badges (StepHeader.renderContextDeclarations): a filled blue chip for
        // "this document hands the resource upward", a plain neutral outline for "something else provides it".
        // Reusing the two accents across the document and step levels is what lets an Exports chip and the
        // step badge for the provide it carries read as the same claim.
        private val exportsAccentColour = Color("#1565c0")
        private val exportsFillColour = Color("rgba(21, 101, 192, 0.10)")
        private val requiresAccentColour = Color("rgba(0, 0, 0, 0.55)")
        private val warningAccentColour = Color("#b26a00")

        private val roleOptions: Array<SelectOption> = arrayOf(
            unsafeJso {
                value = exportsRoleValue
                label = "Exports"
            },
            unsafeJso {
                value = requiresRoleValue
                label = "Needs"
            })
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ContextSignatureEditorState.init(props: ContextSignatureEditorProps) {
        exports = null
        requires = null
        unbackedExports = null
        legacySlots = false
        pickerOptions = null
        adding = false
        addingExports = true
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphDefinitionAttempt.graphStructure.graphNotation

        val documentPath = props.objectLocation.documentPath
        val newExports = LogicContextConventions.documentExports(graphNotation, documentPath)
        val newRequires = LogicContextConventions.documentRequires(graphNotation, documentPath)

        // Both warnings are notation-only and scoped to this document, so they cost one scan of its own objects
        // per publish — no graph walk, no memo.
        val newUnbacked = LogicContextAnalysis.unbackedExports(graphNotation, documentPath)
        val newLegacySlots = LogicContextAnalysis.legacySlotReferences(graphNotation, documentPath).isNotEmpty()

        // All three lists are freshly built Lists of data classes each fire — guard by value (==) so
        // RPureComponent's shallow state comparison doesn't re-render on unchanged content.
        if (newExports == state.exports &&
                newRequires == state.requires &&
                newUnbacked == state.unbackedExports &&
                newLegacySlots == state.legacySlots) {
            return
        }

        setState {
            exports = newExports
            requires = newRequires
            unbackedExports = newUnbacked
            legacySlots = newLegacySlots
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun graphNotation(): GraphNotation? {
        return props.clientStateGlobal.current()?.graphStructure()?.graphNotation
    }


    private fun onStartAdding(exports: Boolean) {
        val graphNotation = graphNotation()
        val options = graphNotation?.let { ContextConventions.allContexts(it) } ?: listOf()

        setState {
            adding = true
            addingExports = exports
            pickerOptions = options
        }
    }


    private fun onCancelAdding() {
        setState {
            adding = false
        }
    }


    private fun onPick(descriptor: ContextDescriptor) {
        val intoExports = state.addingExports
        val graphNotation = graphNotation()

        setState {
            adding = false
        }

        if (graphNotation == null) {
            return
        }

        val exports = rawReferences(
            graphNotation, LogicContextConventions.documentExportsAttributePath).toMutableList()
        val requires = rawReferences(
            graphNotation, LogicContextConventions.documentRequiresAttributePath).toMutableList()

        // A Context is either handed UPWARD from here or asserted to come from a caller — never both. So adding
        // into one role drops it from the other rather than leaving a self-contradictory pair the analysis would
        // then have to arbitrate.
        val target = if (intoExports) exports else requires
        val other = if (intoExports) requires else exports
        other.removeAll { resolvesTo(graphNotation, it, descriptor) }
        if (target.none { resolvesTo(graphNotation, it, descriptor) }) {
            target.add(referenceNameOf(graphNotation, descriptor))
        }

        writeContext(exports, requires)
    }


    private fun onRemove(descriptor: ContextDescriptor, fromExports: Boolean) {
        val graphNotation = graphNotation()
            ?: return

        val exports = rawReferences(
            graphNotation, LogicContextConventions.documentExportsAttributePath).toMutableList()
        val requires = rawReferences(
            graphNotation, LogicContextConventions.documentRequiresAttributePath).toMutableList()

        val target = if (fromExports) exports else requires
        target.removeAll { resolvesTo(graphNotation, it, descriptor) }

        writeContext(exports, requires)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The declaration's raw (unresolved) reference strings, which is what the mutations above edit — resolving
    // first and re-serializing would silently drop a DANGLING entry (one naming a deleted or misspelled
    // Context), and that entry is precisely what the analysis's dangling-reference warning is telling the user
    // to fix. Editing the raw list also preserves each surviving entry's exact spelling.
    private fun rawReferences(graphNotation: GraphNotation, attributePath: AttributePath): List<String> {
        return LogicContextConventions.documentContextReferences(
            graphNotation, props.objectLocation.documentPath, attributePath)
    }


    private fun resolvesTo(
        graphNotation: GraphNotation,
        reference: String,
        descriptor: ContextDescriptor
    ): Boolean {
        return ContextConventions
            .resolveOrNull(graphNotation, reference, props.objectLocation)
            ?.location == descriptor.location
    }


    // Prefer the BARE object name — that is what a hand-written declaration looks like, and Contexts are
    // graph-unique archetypes in practice — but only when it actually resolves back to this Context from the
    // main object. Two same-named Contexts in different documents would otherwise silently bind to whichever
    // one the bare name happens to reach, so that case falls back to the fully-qualified reference.
    private fun referenceNameOf(graphNotation: GraphNotation, descriptor: ContextDescriptor): String {
        val bare = descriptor.location.toReference().crop(retainPath = false).asString()
        if (resolvesTo(graphNotation, bare, descriptor)) {
            return bare
        }
        return descriptor.location.toReference().asString()
    }


    private fun writeContext(exports: List<String>, requires: List<String>) {
        // Empty roles are OMITTED rather than written as `[]`: the `Script` archetype declares `context: {}`,
        // so an absent key reads as "none" identically, and the notation stays as terse as a hand-written one.
        val entries = LinkedHashMap<AttributeSegment, AttributeNotation>()
        if (exports.isNotEmpty()) {
            entries[LogicContextConventions.exportsSegment] = referenceListNotation(exports)
        }
        if (requires.isNotEmpty()) {
            entries[LogicContextConventions.requiresSegment] = referenceListNotation(requires)
        }

        // Anything under `context` that is not one of the two roles above is carried through untouched. The
        // upsert replaces the whole map, so without this a key the editor cannot render — a retired `slots`
        // awaiting hand cleanup, or a signature key added after this component — would be destroyed by an
        // unrelated chip edit. Tested against the ROLE segments rather than the entries built so far, because an
        // emptied role is deliberately absent from those and must not be resurrected from notation.
        val roleSegments = setOf(
            LogicContextConventions.exportsSegment, LogicContextConventions.requiresSegment)

        graphNotation()?.let { graphNotation ->
            for ((segment, notation) in LogicContextConventions.documentContextEntries(
                    graphNotation, props.objectLocation.documentPath)) {
                if (segment !in roleSegments) {
                    entries[segment] = notation
                }
            }
        }

        val contextNotation = MapAttributeNotation(entries.toPersistentMap())

        async {
            props.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                LogicContextConventions.contextAttributeName,
                contextNotation))
        }
    }


    private fun referenceListNotation(references: List<String>): ListAttributeNotation {
        return ListAttributeNotation(
            references.map { ScalarAttributeNotation(it) as AttributeNotation }.toPersistentList())
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val exports = state.exports ?: listOf()
        val requires = state.requires ?: listOf()

        div {
            css {
                // Third float in the stage's top-right stack — below Parameters (0.5em) and Result (2.75em),
                // on the same 2.25em rhythm, and below the reserved error-chip row (StageErrorIndicator).
                // Absolute, so an undeclared document costs no vertical space and the step list still starts
                // at the top.
                position = Position.absolute
                top = (5.0 + StageErrorIndicator.reservedRowEm).em
                right = 0.5.em
                display = Display.flex
                alignItems = AlignItems.center
                // above the step cards (which are positioned but auto z-index) so it stays clickable.
                zIndex = integer(2)
            }

            if (state.adding) {
                renderPicker()
            }
            else {
                renderDeclarations(exports, requires)
            }
        }
    }


    private fun ChildrenBuilder.renderDeclarations(
        exports: List<ContextDescriptor>,
        requires: List<ContextDescriptor>
    ) {
        span {
            css {
                fontSize = 0.8.em
                color = Color("gray")
                marginRight = 0.25.em
            }
            +"Context"
        }

        val unbacked = (state.unbackedExports ?: listOf()).map { it.location }.toSet()

        for (export in exports) {
            val unbackedHere = export.location in unbacked

            renderChip(
                export,
                if (unbackedHere) {
                    "Exports ${export.label()}, but nothing in this document can provide it — no step " +
                            "provides it and no document it runs exports it"
                }
                else {
                    "Exports ${export.label()} — the calling document takes ownership of it, and passes it " +
                            "further up if it exports it too"
                },
                fill = if (unbackedHere) null else exportsFillColour,
                accent = if (unbackedHere) warningAccentColour else exportsAccentColour,
                onDeleteChip = { onRemove(export, fromExports = true) })
        }

        for (required in requires) {
            renderChip(
                required,
                "Needs ${required.label()} — a caller must already have provided it, so running this " +
                        "document directly fails immediately. This assertion also seeds this document's own " +
                        "analysis, so its steps do not report it",
                fill = null,
                accent = requiresAccentColour,
                onDeleteChip = { onRemove(required, fromExports = false) })
        }

        if (state.legacySlots) {
            renderLegacySlotsWarning()
        }

        IconButton {
            title = "Declare a context"
            size = Size.small
            onClick = { onStartAdding(exports = true) }
            icon("material-symbols:add-circle-outline") {}
        }
    }


    // A retired `context.slots` declaration is inert, and the whole-map carry-through in writeContext keeps it
    // that way rather than converting it: `slots` was a claim on the CONSUMER, `exports` an offer on the
    // PROVIDER, so the fix belongs on a different document and only the author knows which.
    private fun ChildrenBuilder.renderLegacySlotsWarning() {
        Tooltip {
            title = ReactNode(
                "This document declares context slots, which has no effect. Declare context exports on the " +
                        "document that provides the resource — the one that decides whether to hand it up — " +
                        "then remove the slots entry from this document's notation")

            span {
                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                    marginRight = 0.25.em
                    fontSize = 0.8.em
                    color = warningAccentColour
                }
                +"context slots has no effect"
            }
        }
    }


    private fun ChildrenBuilder.renderChip(
        descriptor: ContextDescriptor,
        tooltipText: String,
        fill: Color?,
        accent: Color,
        onDeleteChip: () -> Unit
    ) {
        Tooltip {
            title = ReactNode(tooltipText)

            // The span (not the Chip) is the tooltip's ref-bearing child, matching the step badges.
            span {
                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                    marginRight = 0.25.em
                }

                Chip {
                    size = Size.small
                    variant = ChipVariant.outlined
                    label = ReactNode(descriptor.label())
                    icon = Fragment.create {
                        icon(descriptor.icon) {
                            style = unsafeJso {
                                color = accent
                            }
                        }
                    }
                    onDelete = { onDeleteChip() }
                    sx {
                        color = accent
                        borderColor = accent
                        if (fill != null) {
                            backgroundColor = fill
                        }
                    }
                }
            }
        }
    }


    // Two fields: the ROLE (does this document hand the Context up to its caller, or need a caller to have
    // provided it?) and the Context itself. Picking a Context commits and collapses — there is nothing else to
    // confirm, matching the live-apply behaviour of the sibling Result editor.
    private fun ChildrenBuilder.renderPicker() {
        val options = state.pickerOptions ?: listOf()

        // Already-declared Contexts are filtered out: a document exports one or needs one, and re-picking a
        // declared Context in the other role is done by removing its chip first, which reads unambiguously.
        val declared = ((state.exports ?: listOf()) + (state.requires ?: listOf()))
            .map { it.location }
            .toSet()

        val contextOptions = options
            .filter { it.location !in declared }
            .map { descriptor ->
                val option: SelectOption = unsafeJso {
                    value = descriptor.location.asString()
                    label = descriptor.label()
                }
                option
            }
            .toTypedArray()

        span {
            css {
                display = Display.inlineBlock
                width = 7.em
                marginRight = 0.5.em
            }

            muiAutocompleteField(
                label = "Role",
                options = roleOptions,
                selectedOption = roleOptions.find {
                    it.value == (if (state.addingExports) exportsRoleValue else requiresRoleValue)
                },
                onSelect = { picked -> setState { addingExports = picked.value == exportsRoleValue } },
                disableClearable = true,
                opaqueBackground = true)
        }

        span {
            css {
                display = Display.inlineBlock
                width = 11.em
                marginRight = 0.25.em
            }

            muiAutocompleteField(
                label = "Context",
                options = contextOptions,
                selectedOption = null,
                onSelect = { picked ->
                    options.find { it.location.asString() == picked.value }?.let { onPick(it) }
                },
                autoFocus = true,
                openOnFocus = true,
                opaqueBackground = true)
        }

        IconButton {
            title = "Cancel"
            size = Size.small
            onClick = { onCancelAdding() }
            icon("material-symbols:cancel") {}
        }
    }
}
