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
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.iconByName
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectInAttributeCommand
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface StepHeaderProps: Props {
    var objectLocation: ObjectLocation
    var indexInParent: Int

    var managed: Boolean

    var icon: String
    var description: String
    var title: String

    var summary: String?
    var typeMetadata: String?
    var expanded: Boolean?
    var onToggleExpanded: (() -> Unit)?
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
            ClientContext.mirroredGraphStore.apply(RemoveObjectInAttributeCommand(
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
                alignItems = AlignItems.center
                width = 100.pct
                minWidth = 0.px
            }

            renderRunIcon()
            renderNameArea()
            renderRightCluster()
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

            iconByName(props.icon) {
                style = unsafeJso {
                    color = NamedColor.black
                    fontSize = 1.75.em
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderNameArea() {
        div {
            css {
                flexGrow = number(1.0)
                minWidth = 0.px
                display = Display.flex
                flexDirection = FlexDirection.column
            }

            StepNameEditor::class.react {
                objectLocation = props.objectLocation
                title = props.title
                description = props.description
            }

            val summary = props.summary
            if (!summary.isNullOrEmpty()) {
                div {
                    css {
                        color = Color("rgba(0, 0, 0, 0.55)")
                        fontSize = 0.85.em
                        whiteSpace = WhiteSpace.nowrap
                        overflow = Overflow.hidden
                        textOverflow = TextOverflow.ellipsis
                        minWidth = 0.px
                    }

                    +summary
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

                    onClick = { onRemove() }

                    iconByName("Delete") {}
                }
            }

            val expanded = props.expanded
            val onToggleExpanded = props.onToggleExpanded
            if (expanded != null && onToggleExpanded != null) {
                IconButton {
                    title = if (expanded) "Collapse" else "Expand"
                    size = Size.small

                    onClick = { onToggleExpanded() }

                    iconByName(if (expanded) "KeyboardArrowUp" else "KeyboardArrowDown") {}
                }
            }
        }
    }
}
