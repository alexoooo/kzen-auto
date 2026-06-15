package tech.kzen.auto.client.objects.document.script.step.header

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Chip
import mui.material.ChipVariant
import mui.material.IconButton
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.ReactNode
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectInAttributeCommand
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface StepHeaderProps: Props {
    var objectLocation: ObjectLocation
    var indexInParent: Int

    var managed: Boolean

    var icon: String
    var description: String
    var title: String

    var summaryAttributeNames: List<AttributeName>?
    var attributeViewManager: AttributeViewManager.Wrapper?
    var typeMetadata: String?
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
        val containingObjectLocation = props.objectLocation.parent()!!
        val objectAttributePath = attributePathInContainer()

        async {
            props.mirroredGraphStore.apply(RemoveObjectInAttributeCommand(
                containingObjectLocation, objectAttributePath))
        }
    }


    private fun attributePathInContainer(): AttributePath {
        val containingAttribute = props.objectLocation.objectPath.nesting.segments.last().attributePath
        return AttributePath(
            containingAttribute.attribute,
            containingAttribute.nesting.push(AttributeSegment.ofIndex(props.indexInParent)))
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
            // summary lives in a row BELOW this one (not the same flex line), so this row's height is driven
            // only by the constant icon/name/buttons — the icon and right-cluster buttons hold a fixed
            // vertical position whether or not the summary is present (previously the summary grew this row
            // and, under center alignment, shoved them down on collapse).
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

            // Spacer matching the run icon's footprint, so the summary stays indented under the name now
            // that it's a row below the icon/name row (rather than nested in the old name+summary column).
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

            val typeMetadata = props.typeMetadata
            if (!typeMetadata.isNullOrEmpty()) {
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
