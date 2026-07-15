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
import react.Props
import react.ReactNode
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.util.AutoConventions
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

    // True when this step was short-circuited (value-less) by a forward move-to jump — renders a small
    // "Skipped" chip so the grey status bar reads clearly.
    var skipped: Boolean?

    var expanded: Boolean?
    var onToggleExpanded: (() -> Unit)?

    // Breakpoint gutter dot (rendered only when the callback is present — hosts without breakpoint
    // support are unaffected). The unset dot is invisible until the enclosing card's :hover reveals it
    // (the card owns that CSS rule — see ScriptStepDisplayDefault).
    var breakpoint: Boolean?
    var onToggleBreakpoint: (() -> Unit)?

    // Move-to "Set next step here" fallback action (rendered only when the callback is present — i.e. while a
    // settled-paused run of this document exists). canSetNextStep=false renders it disabled with the reason
    // in a tooltip (e.g. a loop-body target). Like the breakpoint dot, it's hover-revealed by the card.
    var onSetNextStep: (() -> Unit)?
    var canSetNextStep: Boolean?
    var setNextStepReason: String?

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

        // The card's hover-reveal rule targets the dot by state, so a SET dot keeps full opacity under hover.
        const val breakpointDotAttribute = "data-breakpoint-dot"
        const val breakpointDotSet = "set"
        const val breakpointDotUnset = "unset"
        private val breakpointColor = Color("#c62828")

        // Marker for the card's hover-reveal rule (see ScriptStepDisplayDefault), plus the action's colours.
        const val setNextStepAttribute = "data-set-next-step"
        private val setNextStepColor = Color("#f9a825")
        private val setNextStepDisabledColor = Color("#9e9e9e")


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

            // Remove the step and its whole nested subtree, deepest-first so each object is a leaf when removed.
            val subtreePaths = documentNotation.objects.notations.map.keys
                .filter { it == objectLocation.objectPath || it.startsWith(objectLocation.objectPath) }
                .sortedByDescending { it.nesting.segments.size }

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


    // Rendered in the right cluster, immediately left of the Delete button.
    private fun ChildrenBuilder.renderBreakpointDot() {
        val onToggleBreakpoint = props.onToggleBreakpoint
            ?: return
        val breakpoint = props.breakpoint ?: false

        div {
            asDynamic()[breakpointDotAttribute] =
                if (breakpoint) { breakpointDotSet } else { breakpointDotUnset }

            css {
                width = 12.px
                height = 12.px
                flexShrink = number(0.0)
                borderRadius = 50.pct
                backgroundColor = breakpointColor
                cursor = Cursor.pointer
                marginRight = 0.5.em
                // The unset dot is invisible; the enclosing card's :hover rule reveals it faintly.
                opacity = number(if (breakpoint) 1.0 else 0.0)
            }

            title = if (breakpoint) { "Remove breakpoint" } else { "Add breakpoint" }

            // stopPropagation: the dot owns the toggle; don't also trip the card's click-to-expand.
            onClick = {
                it.stopPropagation()
                onToggleBreakpoint()
            }
        }
    }


    // Rendered in the right cluster, just left of the breakpoint dot. Host-gated on onSetNextStep, so it
    // only appears while a settled-paused run of this document exists (see ScriptStepDisplayDefault).
    private fun ChildrenBuilder.renderSetNextStepAction() {
        val onSetNextStep = props.onSetNextStep
            ?: return
        val canSet = props.canSetNextStep ?: false
        val tooltipText =
            if (canSet) { "Set next step here" }
            else { props.setNextStepReason ?: "Can't set the next step here" }

        Tooltip {
            title = ReactNode(tooltipText)

            span {
                asDynamic()[setNextStepAttribute] = ""

                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                    marginRight = 0.5.em
                    cursor = if (canSet) { Cursor.pointer } else { Cursor.default }
                    color = if (canSet) { setNextStepColor } else { setNextStepDisabledColor }
                    // Invisible at rest; the enclosing card's :hover reveals it (same idiom as the dot).
                    opacity = number(0.0)
                }

                // stopPropagation: this action owns its click; don't also trip the card's click-to-expand.
                onClick = {
                    it.stopPropagation()
                    if (canSet) {
                        onSetNextStep()
                    }
                }

                icon("material-symbols:play-arrow") {
                    style = unsafeJso {
                        fontSize = 1.1.em
                    }
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
                                color = Color("#d84315")
                                fontSize = 1.25.em
                            }
                        }
                    }
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

            renderSetNextStepAction()

            renderBreakpointDot()

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
}
