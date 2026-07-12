package tech.kzen.auto.client.objects.document.target

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.common.objects.document.target.TargetLocateResult
import tech.kzen.auto.common.objects.document.target.TargetMatchRect
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.resource.ResourceListing
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface TargetViewProps: Props {
    var documentPath: DocumentPath
    var resources: ResourceListing
    var restClient: ClientRestApi

    var screenshotDataUrl: String?
    var locateResult: TargetLocateResult?
    var locating: Boolean?

    var onRemove: (ResourcePath) -> Unit
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Live preview of how the captured patches match the current screenshot: one row per crop
 * (colour swatch, thumbnail, match count, delete) over the screenshot with each match outlined
 * in the crop's colour.
 */
@Suppress("unused")
class TargetView(
    props: TargetViewProps
):
    RPureComponent<TargetViewProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val matchPalette = listOf(
            "#e6194b", "#3cb44b", "#4363d8", "#f58231", "#911eb4", "#008080")

        private fun matchColour(cropIndex: Int): String {
            return matchPalette[cropIndex % matchPalette.size]
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        if (props.resources.digests.isEmpty()) {
            div {
                css {
                    padding = Padding(0.px, 1.em, 1.em, 1.em)
                }
                +"No patches captured yet — use Add to capture one."
            }
            return
        }

        div {
            css {
                padding = Padding(0.px, 1.em, 1.em, 1.em)
            }

            renderCropRows()

            // TODO: tolerance control (per-document match threshold)

            renderOverlay()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderCropRows() {
        val matchesByCrop = props.locateResult?.matchesByCrop

        var cropIndex = 0
        var totalMatches = 0

        for (resourcePath in props.resources.digests.keys) {
            val resourceUri = props.restClient.resourceUri(
                ResourceLocation(props.documentPath, resourcePath))

            val matches = matchesByCrop?.get(resourcePath)
            totalMatches += matches?.size ?: 0

            div {
                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                    marginBottom = 0.25.em
                }

                div {
                    css {
                        width = 1.em
                        height = 1.em
                        flexShrink = number(0.0)
                        backgroundColor = Color(matchColour(cropIndex))
                        marginRight = 0.5.em
                    }
                }

                img {
                    css {
                        maxHeight = 2.em
                        maxWidth = 16.em
                        objectFit = ObjectFit.contain
                        marginRight = 0.5.em
                    }
                    src = resourceUri
                }

                span {
                    css {
                        marginRight = 0.5.em
                    }
                    +resourcePath.asString()
                }

                span {
                    css {
                        marginRight = 0.5.em
                        color = NamedColor.gray
                    }
                    +matchSummary(matches)
                }

                renderDelete(resourcePath)
            }

            cropIndex++
        }

        if (matchesByCrop != null) {
            div {
                css {
                    marginBottom = 0.25.em
                    color =
                        if (totalMatches == 1) {
                            NamedColor.green
                        }
                        else {
                            NamedColor.firebrick
                        }
                }

                +when (totalMatches) {
                    1 -> "1 match — target uniquely located"
                    0 -> "No matches — target not found"
                    else -> "$totalMatches matches — target is not unique (must match exactly one)"
                }
            }
        }
    }


    private fun matchSummary(matches: List<TargetMatchRect>?): String {
        if (matches == null) {
            return when {
                props.locating == true -> "locating…"
                else -> ""
            }
        }

        return when (matches.size) {
            0 -> "no match"
            1 -> "1 match"
            else -> "${matches.size} matches"
        }
    }


    private fun ChildrenBuilder.renderDelete(
        resourcePath: ResourcePath
    ) {
        Button {
            sx {
                backgroundColor = NamedColor.white
            }
            variant = ButtonVariant.outlined
            size = Size.small

            onClick = {
                props.onRemove(resourcePath)
            }

            title = "Delete"

            icon("material-symbols:delete") {
                style = unsafeJso {
                    marginRight = 0.25.em
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderOverlay() {
        val screenshotDataUrl = props.screenshotDataUrl
            ?: return

        val locateResult = props.locateResult

        div {
            css {
                position = Position.relative
                marginTop = 0.5.em
            }

            img {
                css {
                    width = 100.pct
                    display = Display.block
                }
                src = screenshotDataUrl
            }

            if (locateResult != null) {
                val screenshotWidth = locateResult.screenshotWidth.toDouble()
                val screenshotHeight = locateResult.screenshotHeight.toDouble()

                var cropIndex = 0
                for (resourcePath in props.resources.digests.keys) {
                    val colour = matchColour(cropIndex)

                    for (match in locateResult.matchesByCrop[resourcePath].orEmpty()) {
                        div {
                            css {
                                position = Position.absolute
                                left = (100 * match.x / screenshotWidth).pct
                                top = (100 * match.y / screenshotHeight).pct
                                width = (100 * match.width / screenshotWidth).pct
                                height = (100 * match.height / screenshotHeight).pct

                                // Outline (not border) so the ring sits outside the match
                                // without covering its pixels
                                outline = Outline(2.px, LineStyle.solid, Color(colour))
                            }
                        }
                    }

                    cropIndex++
                }
            }
        }
    }
}
