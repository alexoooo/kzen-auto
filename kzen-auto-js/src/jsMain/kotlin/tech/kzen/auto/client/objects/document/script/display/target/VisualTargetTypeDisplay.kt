package tech.kzen.auto.client.objects.document.script.display.target

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import mui.system.sx
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.client.wrap.select.muiAutocompleteField
import tech.kzen.auto.common.objects.document.target.TargetDocument
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions
import web.cssom.*


/**
 * References a Target document: the value row is a document select (with an open-in-new jump),
 * the summary is the document's first crop rendered as a thumbnail.
 */
@Reflect
class VisualTargetTypeDisplay(
    objectLocation: ObjectLocation
): TargetTypeDisplay(objectLocation) {
    override val typeName = "Visual"
    override val editorLabel = "Visual"


    //-----------------------------------------------------------------------------------------------------------------
    private fun resolveLocation(
        value: String?,
        graphNotation: GraphNotation,
        host: ObjectLocation
    ): ObjectLocation? {
        val reference = value?.let { ObjectReference.parse(it) }
            ?: return null

        return graphNotation.coalesce.locateOptional(
            reference, ObjectReferenceHost.ofLocation(host))
    }


    private fun firstCropUri(
        value: String?,
        graphNotation: GraphNotation,
        objectLocation: ObjectLocation,
        resourceUri: (ResourceLocation) -> String
    ): String? {
        val location = resolveLocation(value, graphNotation, objectLocation)
            ?: return null

        val documentPath = location.documentPath
        val documentNotation = graphNotation.documents[documentPath]
        val firstResource = documentNotation?.resources?.digests?.keys?.firstOrNull()
            ?: return null

        return resourceUri(ResourceLocation(documentPath, firstResource))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.renderValueEditor(context: TargetValueEditorContext) {
        val clientState = context.clientState
            ?: return
        val graphNotation = clientState.graphStructure().graphNotation

        val targetMains = graphNotation
            .documents
            .map
            .filter { TargetDocument.isTarget(it.value) }
            .map { ObjectLocation(it.key, NotationConventions.mainObjectPath) }

        val selectOptions = targetMains
            .map {
                val option: SelectOption = unsafeJso {
                    value = it.asString()
                    label = it.documentPath.name.value
                }
                option
            }
            .toTypedArray()

        val selectedLocation = resolveLocation(
            context.value, graphNotation, context.objectLocation)

        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center

                // Clear the floating label's overhang above the field's top border, which
                // otherwise collides with the Target Type field above
                marginTop = 0.75.em
            }

            // The select grows; minWidth 0 lets it shrink so the open button never overflows.
            div {
                css {
                    flexGrow = number(1.0)
                    minWidth = 0.px
                }

                muiAutocompleteField(
                    label = "Target",
                    options = selectOptions,
                    selectedOption = selectOptions.find { it.value == selectedLocation?.asString() },
                    onSelect = {
                        context.onValueChange(
                            ObjectLocation.parse(it.value).toReference().asString())
                    },
                    disableClearable = true)
            }

            IconButton {
                sx {
                    marginLeft = 0.25.em
                }
                title = "Open the selected target"
                disabled = selectedLocation == null

                onClick = {
                    selectedLocation?.let {
                        context.navigationGlobal.goto(it.documentPath)
                    }
                }

                icon("material-symbols:open-in-new") {
                    style = unsafeJso {
                        fontSize = 1.25.em
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.renderSummary(context: TargetSummaryContext) {
        val graphNotation = context.graphStructure.graphNotation

        val resourceUri = firstCropUri(
            context.value, graphNotation, context.objectLocation) {
                context.restClient.resourceUri(it)
            }

        if (resourceUri == null) {
            val location = resolveLocation(context.value, graphNotation, context.objectLocation)
            summaryText("Visual ${location?.documentPath?.name?.value ?: ""}")
            return
        }

        img {
            css {
                maxHeight = 2.em
                maxWidth = 100.pct
                objectFit = ObjectFit.contain
                display = Display.block
            }
            src = resourceUri

            // Never a match when a script automates the kzen-auto UI itself (see TargetLocator)
            asDynamic()[TargetDocument.previewDataAttribute] = ""
        }
    }


    override fun summaryDependencies(
        value: String?,
        clientState: ClientState,
        objectLocation: ObjectLocation
    ): String? {
        // The rendered thumbnail follows the referenced document's first crop
        return firstCropUri(
            value, clientState.graphStructure().graphNotation, objectLocation) { it.toString() }
    }
}
