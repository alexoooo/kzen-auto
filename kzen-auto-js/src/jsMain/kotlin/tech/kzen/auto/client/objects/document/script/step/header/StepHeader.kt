package tech.kzen.auto.client.objects.document.script.step.header

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
import react.create
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.objects.document.common.ObjectSubtreeRemoval
import tech.kzen.auto.common.objects.document.logic.context.ContextDescriptor
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectCommand
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface StepHeaderProps: Props {
    var objectLocation: ObjectLocation

    var managed: Boolean

    var icon: String
    var description: String
    var title: String

    var summaryAttributeNames: List<AttributeName>?
    var attributeViewManager: AttributeViewManager.Wrapper?
    var typeMetadata: String?
    var validationError: String?

    // Advisory validation message (a dangling context reference, a shared resource key) — rendered as an amber
    // icon beside the red-orange error one. Never blocks Run, so it is deliberately a second,
    // differently-coloured indicator rather than a severity applied to the same icon.
    var validationWarning: String?

    // The run-scoped Contexts this step declares, read off notation by the host display
    // (LogicContextConventions) and badged in the right cluster. Null / empty when it declares none.
    var bindsContext: ContextDescriptor?
    // The step's `closePolicy` wire value (`auto` / `manual` / `keepOnFailure`), phrased into the badge's
    // tooltip; null when the step owns no resource, so declares no policy.
    var closePolicy: String?
    // True when the step's OWN document lists the bound Context in `context.exports`, so the caller takes
    // ownership of it. False means it is private to this document and dies at its settle — the distinction
    // the tooltip spells out, and one this document's own signature settles outright.
    var bindsExported: Boolean?
    // For a RunStep: what the hosted document exports (this document takes ownership of each), and the subset
    // this document exports onward rather than owning.
    var hostedExports: List<ContextDescriptor>?
    var hostedExportsContinuingUp: List<ContextDescriptor>?
    var usesContexts: List<ContextDescriptor>?
    var releasesContext: ContextDescriptor?

    // True when this step's value is the Script's own result, supplied implicitly because no Result step
    // names one — the chip is the only place that fact is visible in the editor.
    var isResult: Boolean?

    // True when this step was short-circuited (value-less) by a forward move-to jump — renders a small
    // "Skipped" chip so the grey status bar reads clearly.
    var skipped: Boolean?

    // True when a forward move-to jump skipped over this step MID-FLIGHT and committed the value it had built
    // up so far (a loop's completed iterations). It reads as Done, so the chip is the only thing saying the
    // value is short of what a full run would have produced.
    var partial: Boolean?

    var expanded: Boolean?
    var onToggleExpanded: (() -> Unit)?

    var mirroredGraphStore: MirroredGraphStore
}


//---------------------------------------------------------------------------------------------------------------------
class StepHeader(
    props: StepHeaderProps
):
    RPureComponent<StepHeaderProps, react.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val defaultRunIcon = "PlayArrowIcon"
        const val defaultRunDescription = "Run"

        private val runIconSize = 40.px

        // Marks the header's top row (run icon · name · right cluster) as this step's "line" — the execution
        // margin measures it to anchor the next-to-run arrow and the breakpoint dot beside the title rather
        // than at the card's vertical middle. One marker covers every StepHeader host despite their differing
        // paddings (leaf card, branchHeaderSlab), and querySelector is document-first, so a container row
        // resolves to its OWN header, not a nested step's.
        const val stepHeaderRowAttribute = "data-step-header"

        // Mirrors ScriptStepDisplayDefault.validationErrorColour / .validationWarningColour — the same two
        // accents the card's 4px status bar uses, so the bar and the icon that explains it always agree.
        private val validationErrorColour = Color("#d84315")
        private val validationWarningColour = Color("#f9a825")

        // The binds badge's accent: a blue that appears nowhere in the run-status palette (gold / green /
        // red / grey / white), so "this step binds a value into scope" can never be misread as a run outcome.
        private val bindsAccentColour = Color("#1565c0")
        private val bindsFillColour = Color("rgba(21, 101, 192, 0.10)")

        // uses reads as an ordinary neutral outline (it merely consumes); releases is muted and dashed —
        // a closer is never gated and never ambered, so it must not compete with the two consumer states.
        private val usesAccentColour = Color("rgba(0, 0, 0, 0.55)")
        private val releasesAccentColour = Color("rgba(0, 0, 0, 0.40)")


        fun icon(graphStructure: GraphStructure, objectLocation: ObjectLocation): String {
            return graphStructure.graphNotation
                .firstAttribute(objectLocation, AutoConventions.iconAttributePath)
                ?.asString()
                ?: defaultRunIcon
        }

        fun description(graphStructure: GraphStructure, objectLocation: ObjectLocation): String {
            return graphStructure.graphNotation
                .firstAttribute(objectLocation, AutoConventions.descriptionAttributePath)
                ?.asString()
                ?: defaultRunDescription
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRemove() {
        val objectLocation = props.objectLocation

        async {
            val documentNotation = props.mirroredGraphStore.graphNotation()
                .documents[objectLocation.documentPath]
                ?: return@async

            val subtreePaths = ObjectSubtreeRemoval.deepestFirst(
                documentNotation.objects.notations.map.keys, objectLocation.objectPath)

            for (objectPath in subtreePaths) {
                props.mirroredGraphStore.apply(RemoveObjectCommand(
                    ObjectLocation(objectLocation.documentPath, objectPath)))
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
                width = 100.pct
                minWidth = 0.px
            }

            // NB: no toggle handler here — the enclosing step card owns click-to-toggle (so its padding /
            //     outskirts are clickable too). Header clicks just bubble up to it; the handled controls
            //     within (name text, pencil, delete, chevron) stop propagation so they don't also toggle.

            // Top row: run icon · name · action buttons, centred against each other. The collapsed-state
            // summary lives in its own row BELOW, so this row's height is constant and the icon/buttons hold
            // a fixed vertical position — on a shared flex line the summary would grow the row and, under
            // centre alignment, shove them down on collapse.
            div {
                asDynamic()[stepHeaderRowAttribute] = ""

                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                    width = 100.pct
                    minWidth = 0.px
                }

                renderRunIcon()
                renderName()
                renderRightCluster()
            }

            renderSummary()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderRunIcon() {
        div {
            css {
                width = runIconSize
                height = runIconSize
                flexShrink = number(0.0)
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                marginRight = 0.25.em
            }

            title = props.description

            icon(props.icon) {
                style = unsafeJso {
                    color = NamedColor.black
                    fontSize = 1.75.em
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderName() {
        div {
            css {
                flexGrow = number(1.0)
                minWidth = 0.px
            }

            StepNameEditor::class.react {
                objectLocation = props.objectLocation
                title = props.title
                description = props.description
                mirroredGraphStore = props.mirroredGraphStore
            }
        }
    }


    private fun ChildrenBuilder.renderSummary() {
        if (props.expanded == true) {
            return
        }

        val summaryAttributeNames = props.summaryAttributeNames
            ?: return
        val attributeViewManager = props.attributeViewManager
            ?: return

        div {
            css {
                display = Display.flex
                minWidth = 0.px
            }

            // Spacer matching the run icon's footprint, so the summary row below stays indented under the name.
            div {
                css {
                    width = runIconSize
                    flexShrink = number(0.0)
                    marginRight = 0.25.em
                }
            }

            div {
                css {
                    flexGrow = number(1.0)
                    minWidth = 0.px
                }

                for (summaryAttributeName in summaryAttributeNames) {
                    div {
                        key = react.Key(summaryAttributeName.value)

                        attributeViewManager.child(this) {
                            objectLocation = props.objectLocation
                            attributeName = summaryAttributeName
                        }
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderRightCluster() {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                flexShrink = number(0.0)
                marginLeft = 0.5.em
            }

            // Validation error: a distinct red-orange icon (different from the darker run-failure red) with
            // the message in a tooltip — surfaced on the collapsed card so a broken step is visible at a
            // glance. No stopPropagation, so a click still bubbles to the card's expand-to-see-detail.
            val validationError = props.validationError
            if (validationError != null) {
                Tooltip {
                    title = ReactNode(validationError)

                    span {
                        css {
                            display = Display.flex
                            alignItems = AlignItems.center
                            marginRight = 0.5.em
                        }

                        icon("material-symbols:error") {
                            style = unsafeJso {
                                color = validationErrorColour
                                fontSize = 1.25.em
                            }
                        }
                    }
                }
            }

            // Validation warning: the advisory sibling of the block above — a different icon AND a different
            // colour, so it can never be mistaken for an error. Both can be present at once (a step may fail
            // to compile AND ask for a Context nothing binds), and they read left-to-right worst-first.
            val validationWarning = props.validationWarning
            if (validationWarning != null) {
                Tooltip {
                    title = ReactNode(validationWarning)

                    span {
                        css {
                            display = Display.flex
                            alignItems = AlignItems.center
                            marginRight = 0.5.em
                        }

                        icon("material-symbols:warning") {
                            style = unsafeJso {
                                color = validationWarningColour
                                fontSize = 1.25.em
                            }
                        }
                    }
                }
            }

            // Result chip: this step's value is what the Script yields. An identity marker rather than a run
            // outcome, so it leads the chips — but still follows the two validation icons.
            if (props.isResult == true) {
                Chip {
                    sx {
                        marginRight = 0.5.em
                    }
                    size = Size.small
                    label = ReactNode("Result")
                    variant = ChipVariant.outlined
                }
            }

            // Skipped chip: shown when a move-to jump skipped over this step (grey status bar). Placed
            // before the type chip so it reads first.
            if (props.skipped == true) {
                Chip {
                    sx {
                        marginRight = 0.5.em
                    }
                    size = Size.small
                    label = ReactNode("Skipped")
                    variant = ChipVariant.outlined
                }
            }

            // Partial chip: the jump's other outcome — skipped over, but mid-flight and holding a value worth
            // committing. Mutually exclusive with Skipped (a step is one or the other), and placed alongside it.
            if (props.partial == true) {
                Chip {
                    sx {
                        marginRight = 0.5.em
                    }
                    size = Size.small
                    label = ReactNode("Partial")
                    variant = ChipVariant.outlined
                }
            }

            // Type chip, but not for void (Unit) steps — a "[Unit]" badge conveys nothing.
            val typeMetadata = props.typeMetadata
            if (!typeMetadata.isNullOrEmpty() && typeMetadata != "Unit") {
                Chip {
                    sx {
                        marginRight = 0.5.em
                    }
                    size = Size.small
                    label = ReactNode(typeMetadata)
                    variant = ChipVariant.outlined
                }
            }

            renderContextDeclarations()

            if (!props.managed) {
                IconButton {
                    title = "Delete"
                    size = Size.small

                    // stopPropagation: this button handles its own click; don't also trip the card's click-to-expand.
                    onClick = {
                        it.stopPropagation()
                        onRemove()
                    }

                    icon("material-symbols:delete") {}
                }
            }

            val expanded = props.expanded
            val onToggleExpanded = props.onToggleExpanded
            if (expanded != null && onToggleExpanded != null) {
                IconButton {
                    title = if (expanded) "Collapse" else "Expand"
                    size = Size.small

                    // stopPropagation: the chevron owns the toggle; don't let the click also reach the card's expand.
                    onClick = {
                        it.stopPropagation()
                        onToggleExpanded()
                    }

                    icon(if (expanded) "KeyboardArrowUp" else "KeyboardArrowDown") {}
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The step's run-scoped Context declarations, as badges that must read as DIFFERENT things — a step that
    // binds a value into scope, one that receives a hosted document's export, one that merely reads it, and one
    // that closes it are not interchangeable, and an identical chip for each would say they are. The role is carried
    // by the chip's own skin (filled blue solid / filled blue dotted / plain outline / dashed muted), the
    // identity by the Context's own icon + label, and the semantics (close policy, where ownership rests, why a
    // closer is never gated) by the tooltip.
    private fun ChildrenBuilder.renderContextDeclarations() {
        props.bindsContext?.let { bound ->
            val scope =
                if (props.bindsExported == true) {
                    "exported: the calling document takes ownership, and passes it further up if it exports " +
                            "it too"
                }
                else {
                    "private to this document: disposed when it settles"
                }

            contextBadge(
                bound,
                listOfNotNull(
                    "Binds ${bound.label()}",
                    closePolicyPhrase(props.closePolicy),
                    scope
                ).joinToString(" — "),
                fill = bindsFillColour,
                accent = bindsAccentColour,
                borderLine = LineStyle.solid)
        }

        props.hostedExports?.forEach { hosted ->
            val continuingUp = props.hostedExportsContinuingUp?.any { it.location == hosted.location } == true

            contextBadge(
                hosted,
                if (continuingUp) {
                    "The hosted document exports ${hosted.label()}, and this document exports it onward — " +
                            "so a caller of this one owns it"
                }
                else {
                    "The hosted document exports ${hosted.label()} — this document takes ownership, and " +
                            "disposes it when it settles"
                },
                fill = bindsFillColour,
                accent = bindsAccentColour,
                borderLine = LineStyle.dotted)
        }

        props.usesContexts?.forEach { used ->
            contextBadge(
                used,
                "Uses ${used.label()} — a step before this one must bind it, a document it runs " +
                        "must export it, or this document's context requires must declare that a caller does",
                fill = null,
                accent = usesAccentColour,
                borderLine = LineStyle.solid)
        }

        props.releasesContext?.let { released ->
            contextBadge(
                released,
                "Releases ${released.label()} — this step closes it. A closer's job is to make the absence " +
                        "true, so it is never gated and never warned about when the Context is already gone",
                fill = null,
                accent = releasesAccentColour,
                borderLine = LineStyle.dashed)
        }
    }


    private fun ChildrenBuilder.contextBadge(
        descriptor: ContextDescriptor,
        tooltipText: String,
        fill: Color?,
        accent: Color,
        borderLine: LineStyle
    ) {
        Tooltip {
            title = ReactNode(tooltipText)

            // The span (not the Chip) is the tooltip's ref-bearing child and the flex item, matching the
            // validation-icon blocks above. No stopPropagation: a click still bubbles to the card's expand.
            span {
                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                    marginRight = 0.5.em
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
                    sx {
                        color = accent
                        borderColor = accent
                        borderStyle = borderLine
                        if (fill != null) {
                            backgroundColor = fill
                        }
                    }
                }
            }
        }
    }


    // The `closePolicy` wire value as the clause the tooltip needs. Unknown / absent yields null so the
    // tooltip simply omits the clause rather than asserting a policy the notation never declared.
    private fun closePolicyPhrase(closePolicy: String?): String? {
        return when (closePolicy) {
            ResourceClosePolicy.Auto.key ->
                "closed when the owning document finishes"

            ResourceClosePolicy.Manual.key ->
                "kept open until an explicit close step disposes it"

            ResourceClosePolicy.KeepOnFailure.key ->
                "closed on the owner's success or cancel, kept on failure to inspect"

            else ->
                null
        }
    }
}
