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
/**
 * Which half of the context signature one editor instance owns. The two are separate floating rows rather than
 * one row with a role selector, because the question each answers is different — what a caller must already
 * have set up, versus what running this document sets up — and a single row made the reader choose the role
 * before it could show them anything.
 */
enum class ContextSignatureRole {
    Requires,
    Provides
}


//---------------------------------------------------------------------------------------------------------------------
external interface ContextSignatureEditorProps: ObjectScopedProps {
    var mirroredGraphStore: MirroredGraphStore
    var role: ContextSignatureRole
}


external interface ContextSignatureEditorState: State {
    // The document's own `context.exports` / `context.requires`, resolved. BOTH roles are held whichever row
    // this is: a Context belongs to one role or the other, so adding into this row must drop it from the
    // other, and the picker must not offer what the other row already declares. Null until the first client
    // state.
    var exports: List<ContextDescriptor>?
    var requires: List<ContextDescriptor>?

    // Provides-row only, all three notation-derived and left empty on the Requires row rather than computed
    // and ignored: the Contexts this document's own steps bind and keep (rendered read-only beside the
    // exports), which declared exports nothing in the document can back, and whether a retired
    // `context.slots` declaration is still present. Document-level warnings have no other rendering surface —
    // the validation channel carries document-level ERRORS only.
    var privateProvides: List<ContextDescriptor>?
    var unbackedExports: List<ContextDescriptor>?
    var legacySlots: Boolean

    // Every `is: Context` in the graph — the picker's options. Populated ON DEMAND (when the picker opens)
    // rather than per publish: ContextConventions.allContexts walks the inheritance chain of every object in
    // the coalesced graph, which is far too much work to repeat on each ClientState broadcast for a list the
    // user sees only while adding.
    var pickerOptions: List<ContextDescriptor>?

    // The collapsed chip row expands into the picker when true.
    var adding: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * One row of a Logic document's run-scoped **Context** signature, selected by [ContextSignatureEditorProps.role]:
 * the `context.requires` it asserts a CALLER has already provided, or the `context.exports` it offers upward (a
 * step provide of one climbs past this document's frame to its caller; anything not listed is private to this
 * document and disposed at its settle). `context` is data declared `by: Nominal` (weak references, never
 * constructor-injected) and has no generic attribute editor, so this reads and writes it directly.
 *
 * The Provides row also shows what this document opens and KEEPS — derived from its steps, so read-only, and
 * the only surface on which a private open is visible at all. Exported and private carry a worded badge rather
 * than only a fill, so the distinction survives a reader who cannot separate the two colours.
 *
 * Floated at the top-right of the stage beneath Parameters and Result, and therefore with ZERO flow footprint:
 * a document that declares no Context costs no vertical space, and — the load-bearing part — both instances are
 * emitted UNCONDITIONALLY from `ScriptController.renderSignature`, so the step subtree's child index never
 * shifts (see the comment on the document-level error slot there).
 *
 * Writes upsert the WHOLE `context` map (mirroring [ResultSignatureEditor]'s whole-`results` upsert) rather
 * than inserting into one key. That is necessary, not merely simpler: `context` is inherited from the `Script`
 * archetype (`context: {}`) and does not exist on a document's own `main` until the first edit, so a
 * list-insert command would have no local attribute to land in. Both roles are re-read from notation on every
 * write, so the sibling row's entries survive this row's edit; entries are written as LIST items, matching the
 * archetype's `is: Map, of: [String, {is: List, of: ObjectLocation}]` meta declaration; any `context` key
 * neither row recognizes is carried through verbatim, so a whole-map upsert never destroys one.
 */
class ContextSignatureEditor:
    ObjectScopedComponent<ContextSignatureEditorProps, ContextSignatureEditorState>()
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Same skin language as the step badges (StepHeader.renderContextDeclarations): a filled blue chip for
        // "this document hands the resource upward", a plain neutral outline for "something else provides it".
        // Reusing the two accents across the document and step levels is what lets an Exports chip and the
        // step badge for the provide it carries read as the same claim.
        private val exportsAccentColour = Color("#1565c0")
        private val exportsFillColour = Color("rgba(21, 101, 192, 0.10)")
        private val requiresAccentColour = Color("rgba(0, 0, 0, 0.55)")
        private val warningAccentColour = Color("#b26a00")

        // Vertical rhythm of the stage's top-right float stack, shared with Parameters (0.5em) and Result
        // (2.75em) above.
        private const val requiresRowEm = 5.0
        private const val providesRowEm = 7.25
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ContextSignatureEditorState.init(props: ContextSignatureEditorProps) {
        exports = null
        requires = null
        privateProvides = null
        unbackedExports = null
        legacySlots = false
        pickerOptions = null
        adding = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun provides(): Boolean {
        return props.role == ContextSignatureRole.Provides
    }


    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphDefinitionAttempt.graphStructure.graphNotation

        val documentPath = props.objectLocation.documentPath
        val newExports = LogicContextConventions.documentExports(graphNotation, documentPath)
        val newRequires = LogicContextConventions.documentRequires(graphNotation, documentPath)

        // All three are notation-only and scoped to this document, so they cost a scan of its own objects per
        // publish — no graph walk, no memo — and the Requires row, which renders none of them, pays nothing.
        val provides = provides()
        val newPrivate = if (provides) LogicContextAnalysis.privateProvides(graphNotation, documentPath)
            else listOf()
        val newUnbacked = if (provides) LogicContextAnalysis.unbackedExports(graphNotation, documentPath)
            else listOf()
        val newLegacySlots = provides &&
                LogicContextAnalysis.legacySlotReferences(graphNotation, documentPath).isNotEmpty()

        // Every list is freshly built of data classes each fire — guard by value (==) so RPureComponent's
        // shallow state comparison doesn't re-render on unchanged content.
        if (newExports == state.exports &&
                newRequires == state.requires &&
                newPrivate == state.privateProvides &&
                newUnbacked == state.unbackedExports &&
                newLegacySlots == state.legacySlots) {
            return
        }

        setState {
            exports = newExports
            requires = newRequires
            privateProvides = newPrivate
            unbackedExports = newUnbacked
            legacySlots = newLegacySlots
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun graphNotation(): GraphNotation? {
        return props.clientStateGlobal.current()?.graphStructure()?.graphNotation
    }


    private fun onStartAdding() {
        val graphNotation = graphNotation()
        val options = graphNotation?.let { ContextConventions.allContexts(it) } ?: listOf()

        setState {
            adding = true
            pickerOptions = options
        }
    }


    private fun onCancelAdding() {
        setState {
            adding = false
        }
    }


    private fun onPick(descriptor: ContextDescriptor) {
        val intoExports = provides()
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


    private fun onRemove(descriptor: ContextDescriptor) {
        val graphNotation = graphNotation()
            ?: return

        val exports = rawReferences(
            graphNotation, LogicContextConventions.documentExportsAttributePath).toMutableList()
        val requires = rawReferences(
            graphNotation, LogicContextConventions.documentRequiresAttributePath).toMutableList()

        val target = if (provides()) exports else requires
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
        // upsert replaces the whole map, so without this a key neither row can render — a retired `slots`
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
        div {
            css {
                // Third and fourth floats in the stage's top-right stack — below Parameters (0.5em) and Result
                // (2.75em), on the same 2.25em rhythm, and below the reserved error-chip row
                // (StageErrorIndicator). Absolute, so an undeclared document costs no vertical space and the
                // step list still starts at the top.
                position = Position.absolute
                top = ((if (provides()) providesRowEm else requiresRowEm) + StageErrorIndicator.reservedRowEm).em
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
                renderDeclarations()
            }
        }
    }


    private fun ChildrenBuilder.renderDeclarations() {
        span {
            css {
                fontSize = 0.8.em
                color = Color("gray")
                marginRight = 0.25.em
            }
            +(if (provides()) "Provides" else "Requires")
        }

        if (provides()) {
            renderProvides()
        }
        else {
            renderRequires()
        }

        IconButton {
            title = if (provides()) "Declare a context this document provides"
                else "Declare a context a caller must provide"
            size = Size.small
            onClick = { onStartAdding() }
            icon("material-symbols:add-circle-outline") {}
        }
    }


    private fun ChildrenBuilder.renderRequires() {
        for (required in state.requires ?: listOf()) {
            renderChip(
                required,
                "Needs ${required.label()} — a caller must already have provided it, so running this " +
                        "document directly fails immediately. This assertion also seeds this document's own " +
                        "analysis, so its steps do not report it",
                badge = null,
                fill = null,
                accent = requiresAccentColour,
                onDeleteChip = { onRemove(required) })
        }
    }


    private fun ChildrenBuilder.renderProvides() {
        val unbacked = (state.unbackedExports ?: listOf()).map { it.location }.toSet()

        for (export in state.exports ?: listOf()) {
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
                badge = "Exported",
                fill = if (unbackedHere) null else exportsFillColour,
                accent = if (unbackedHere) warningAccentColour else exportsAccentColour,
                onDeleteChip = { onRemove(export) })
        }

        // Read-only: a private open is what a step of this document does, so there is no declaration to delete.
        // Exporting it is how it stops being private, and the picker above is where that is done.
        for (private in state.privateProvides ?: listOf()) {
            renderChip(
                private,
                "A step of this document provides ${private.label()} and this document keeps it — it is " +
                        "readable here and below, and disposed when this document settles. Declare it as " +
                        "provided to hand it to the caller instead",
                badge = "Private",
                fill = null,
                accent = requiresAccentColour,
                onDeleteChip = null)
        }

        if (state.legacySlots) {
            renderLegacySlotsWarning()
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
        badge: String?,
        fill: Color?,
        accent: Color,
        onDeleteChip: (() -> Unit)?
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
                    label = Fragment.create {
                        +descriptor.label()
                        if (badge != null) {
                            renderBadge(badge, accent)
                        }
                    }
                    icon = Fragment.create {
                        icon(descriptor.icon) {
                            style = unsafeJso {
                                color = accent
                            }
                        }
                    }
                    if (onDeleteChip != null) {
                        onDelete = { onDeleteChip() }
                    }
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


    // Worded, because fill alone cannot carry "handed to the caller" versus "kept here" — the two chips differ
    // by a background tint a reader may not perceive, and the tooltip is hover-only.
    private fun ChildrenBuilder.renderBadge(badge: String, accent: Color) {
        span {
            css {
                marginLeft = 0.4.em
                paddingLeft = 0.3.em
                paddingRight = 0.3.em
                fontSize = 0.65.em
                letterSpacing = 0.05.em
                textTransform = TextTransform.uppercase
                borderRadius = 0.25.em
                borderStyle = LineStyle.solid
                borderWidth = 1.px
                borderColor = accent
            }
            +badge
        }
    }


    // One field: the Context this row declares. Picking commits and collapses — the role is the row, so there
    // is nothing else to confirm, matching the live-apply behaviour of the sibling Result editor.
    private fun ChildrenBuilder.renderPicker() {
        val options = state.pickerOptions ?: listOf()

        // Already-declared Contexts are filtered out: a document provides one or requires one, and re-picking a
        // declared Context in the other row is done by removing its chip first, which reads unambiguously.
        val declared = ((state.exports ?: listOf()) + (state.requires ?: listOf()))
            .map { it.location }
            .toSet()

        val contextOptions = options
            .filter { it.location !in declared }
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

        span {
            css {
                display = Display.inlineBlock
                width = 15.em
                marginRight = 0.25.em
            }

            muiAutocompleteField(
                label = if (provides()) "Provides" else "Requires",
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


    // The value contract and the description are both read from the Context's own notation and are what tell
    // two similarly-named declarations apart; the row shows the type unqualified, with the qualified class on
    // hover.
    private fun optionDetailOf(descriptor: ContextDescriptor): String? {
        val parts = listOfNotNull(
            descriptor.typeLabel().ifEmpty { null },
            descriptor.description.ifEmpty { null })
        return parts.joinToString(" — ").ifEmpty { null }
    }
}
