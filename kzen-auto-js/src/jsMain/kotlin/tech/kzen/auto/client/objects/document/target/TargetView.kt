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
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.select
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.common.objects.document.target.TargetCropMatches
import tech.kzen.auto.common.objects.document.target.TargetDocument
import tech.kzen.auto.common.objects.document.target.TargetLocateResult
import tech.kzen.auto.common.objects.document.target.TargetMatchRect
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.resource.ResourceListing
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import web.cssom.*
import kotlin.math.round


//---------------------------------------------------------------------------------------------------------------------
external interface TargetViewProps: Props {
    var documentPath: DocumentPath
    var resources: ResourceListing
    var restClient: ClientRestApi

    var screenshotDataUrl: String?
    var locateResult: TargetLocateResult?
    var locating: Boolean?

    // Match-score threshold from the document notation; null = exact-only
    var tolerance: Double?

    var onRemove: (ResourcePath) -> Unit
    var onToleranceChange: (Double) -> Unit
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


        // Calibrated against the rasterization-drift fixture (same-machine desktop capture
        // scores ~0.85 against a fresh capture — Normal catches it, Strict doesn't); see
        // TemplateMatcherTest.rasterizationDriftFoundAtNormalTolerance
        private val tolerancePresets = listOf(
            "Exact" to TargetDocument.exactTolerance,
            "Strict" to 0.9,
            "Normal" to 0.8,
            "Loose" to 0.7)


        private fun formatScore(score: Double): String {
            return (round(score * 100) / 100).toString()
        }


        private fun matchAnnotation(match: TargetMatchRect): String? {
            if (match.score >= TargetDocument.exactTolerance) {
                return null
            }
            val scoreText = formatScore(match.score)
            return when (match.scale) {
                1.0 -> scoreText
                else -> "$scoreText ×${match.scale}"
            }
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

            renderToleranceControl()

            renderOverlay()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderCropRows() {
        val matchesByCrop = props.locateResult?.matchesByCrop

        var cropIndex = 0
        val allMatches = mutableListOf<TargetMatchRect>()

        for (resourcePath in props.resources.digests.keys) {
            val resourceUri = props.restClient.resourceUri(
                ResourceLocation(props.documentPath, resourcePath))

            val matches = matchesByCrop?.get(resourcePath)
            allMatches.addAll(matches?.matches.orEmpty())

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

                    // Never a match when a script automates the kzen-auto UI itself (see TargetLocator)
                    asDynamic()[TargetDocument.previewDataAttribute] = ""
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
            // Overlapping matches from different crops are the same target (mirrors the click
            // path's collapse of matches that resolve to the same element)
            val distinctTargets = TargetLocateResult.distinctTargetCount(allMatches)

            div {
                css {
                    marginBottom = 0.25.em
                    color =
                        if (distinctTargets == 1) {
                            NamedColor.green
                        }
                        else {
                            NamedColor.firebrick
                        }
                }

                +when {
                    distinctTargets == 0 ->
                        "No matches — target not found"

                    distinctTargets == 1 && allMatches.size == 1 ->
                        "1 match — target uniquely located"

                    distinctTargets == 1 ->
                        "${allMatches.size} matches agree on one target — uniquely located"

                    else ->
                        "$distinctTargets distinct targets — target is not unique " +
                                "(must match exactly one)"
                }
            }
        }
    }


    private fun matchSummary(cropMatches: TargetCropMatches?): String {
        if (cropMatches == null) {
            return when {
                props.locating == true -> "locating…"
                else -> ""
            }
        }

        val matches = cropMatches.matches

        if (matches.isEmpty()) {
            val closest = cropMatches.closest
                ?: return "no match"
            return "no match (closest ${formatScore(closest.score)} at [${closest.x}, ${closest.y}])"
        }

        val count = when (matches.size) {
            1 -> "1 match"
            else -> "${matches.size} matches"
        }

        val annotation = matchAnnotation(matches.maxBy { it.score })
            ?: return count
        return "$count ($annotation)"
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
    /**
     * Match-score threshold for tolerant matching, written to the document (`tolerance:` on the
     * main object): Exact keeps today's pixel-equality behaviour; lower presets accept
     * rasterization drift (Normal) or bolder rendering differences (Loose). Re-locates live.
     */
    private fun ChildrenBuilder.renderToleranceControl() {
        val effective = props.tolerance ?: TargetDocument.exactTolerance
        val custom = tolerancePresets.none { it.second == effective }

        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                gap = 0.5.em
                marginBottom = 0.25.em
            }

            span {
                +"Tolerance:"
            }

            select {
                value = effective.toString()
                onChange = {
                    val selected = it.currentTarget.value.toDoubleOrNull()
                    if (selected != null && selected != effective) {
                        props.onToleranceChange(selected)
                    }
                }

                for ((label, presetValue) in tolerancePresets) {
                    option {
                        value = presetValue.toString()
                        +label
                    }
                }

                if (custom) {
                    // A hand-edited threshold that isn't one of the presets
                    option {
                        value = effective.toString()
                        +"Custom ($effective)"
                    }
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

            // The displayed screenshot can itself contain the target's pixels — never a match
            // when a script automates the kzen-auto UI itself (see TargetLocator)
            asDynamic()[TargetDocument.previewDataAttribute] = ""

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

                    for (match in locateResult.matchesByCrop[resourcePath]?.matches.orEmpty()) {
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

                            val annotation = matchAnnotation(match)
                            if (annotation != null) {
                                div {
                                    css {
                                        position = Position.absolute
                                        bottom = 100.pct
                                        left = (-2).px
                                        padding = Padding(0.px, 2.px)
                                        backgroundColor = Color(colour)
                                        color = NamedColor.white
                                        fontSize = 10.px
                                        lineHeight = number(1.4)
                                        whiteSpace = WhiteSpace.nowrap
                                    }
                                    +annotation
                                }
                            }
                        }
                    }

                    cropIndex++
                }
            }
        }
    }
}
