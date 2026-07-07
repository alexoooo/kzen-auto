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
