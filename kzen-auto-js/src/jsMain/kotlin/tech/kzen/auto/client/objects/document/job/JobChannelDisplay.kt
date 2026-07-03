package tech.kzen.auto.client.objects.document.job

import emotion.react.css
import js.reflect.unsafeCast
import mui.material.IconButton
import mui.material.Size
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
// The gold pipe palette + chevron silhouette, shared by the collapsed chevron and the expanded config card so the
// two surfaces can't drift.
internal object JobChannelDisplayStyle {
    val fill = Color("#fff7d6")
    val border = Color("#e8c200")
    val accent = Color("#9a7b00")

    // A downward-pointing arrow/chevron: a rectangular top that tapers to a bottom point (a fixed-px taper, so
    // it reads the same on the short pill and the taller card), evoking flow into the Worker below. Because a
    // clip-path suppresses CSS borders on its diagonal edges, chevron surfaces use a solid fill, not a border.
    val downwardChevron: ClipPath =
        "polygon(0% 0%, 100% 0%, 100% calc(100% - 14px), 50% 100%, 0% calc(100% - 14px))"
            .unsafeCast<ClipPath>()

    // A thin margin below the whole channel so its downward point doesn't touch the following Worker card.
    val marginBottom = 0.5.em
}


//---------------------------------------------------------------------------------------------------------------------
external interface JobChannelDisplayProps: Props {
    var upstreamName: String
    var downstreamName: String

    // The upstream Worker whose per-output config (batchSize / capacity) this channel carries, and the output
    // port that config is keyed under. Handed back on toggle / clear so JobController opens this channel's inline
    // editor or removes its override. Both come off the value-gated Connection instance so they stay
    // reference-stable across the controller's drag / progress re-renders.
    var upstreamWorker: ObjectLocation
    var outputPort: AttributeName

    // The channel's current EFFECTIVE batchSize / capacity (Worker override, else Job default): shown in the
    // collapsed tooltip and — when customized — the collapsed caption.
    var batchSize: String
    var capacity: String

    // The effective default a field falls back to (shows as a greyed placeholder) when the Worker carries no
    // override: the Job-wide default, else the archetype default.
    var batchSizeFallback: String
    var capacityFallback: String

    // This channel's EXPLICIT per-knob overrides (the Worker's own value), or null when that knob inherits the
    // Job-wide default. Drives a bolder chevron and the collapsed caption, which lists ONLY the overridden knobs
    // (an inherited knob is omitted) so a customized channel's actual overrides are visible without expanding.
    var batchSizeOverride: String?
    var capacityOverride: String?

    // Local UI toggle from JobController: collapsed chevron vs the inline config card.
    var expanded: Boolean

    var onToggle: (upstreamWorker: ObjectLocation) -> Unit
    var onClear: (upstreamWorker: ObjectLocation, outputPort: AttributeName) -> Unit

    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}


//---------------------------------------------------------------------------------------------------------------------
// The gold channel drawn in the gap between two adjacent Worker cards the order-driven rule connects, echoing
// Flow's Pipe so a connector reads distinctly from a node. The channel is synthesized + order-managed, so the
// saved notation keeps Worker ports blank and carries no Channel objects on the common path. This one component
// owns BOTH states: collapsed (a downward chevron; clicking it opens the editor) and expanded (an inline card
// that tunes this channel's batchSize / capacity, stored on the upstream Worker's `channels.<port>` map so they
// follow it across rename / reorder). A memoized RPureComponent (props are ===-stable strings / booleans + two
// stable callbacks + stable service refs) so JobController's frequent drag-hover re-renders, which don't change
// any channel's props, bail out here.
class JobChannelDisplay(
    props: JobChannelDisplayProps
):
    RPureComponent<JobChannelDisplayProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // The channel's EFFECTIVE value for [knob]: the upstream Worker's own non-blank value for that output
        // port, else the document-level default. Mirrors the server precedence (Worker output config > Job
        // default > archetype).
        fun effectiveChannelValue(
            graphNotation: GraphNotation,
            workerLocation: ObjectLocation,
            mainLocation: ObjectLocation,
            outputPort: AttributeName,
            knob: AttributeName,
            archetypeDefault: String
        ): String {
            ownChannelValue(graphNotation, workerLocation, outputPort, knob)?.let { return it }
            return effectiveDefaultValue(graphNotation, mainLocation, knob, archetypeDefault)
        }


        // The document-level default for [knob] a customization falls back to: the Job-wide value on `main`
        // (flat, blank treated as unset), else the archetype default. This is the fallback shown by a config
        // field when the Worker's own value is unset.
        fun effectiveDefaultValue(
            graphNotation: GraphNotation,
            mainLocation: ObjectLocation,
            knob: AttributeName,
            archetypeDefault: String
        ): String {
            val jobDefault = graphNotation.firstAttribute(mainLocation, AttributePath.ofName(knob))
                ?.asString()?.ifBlank { null }
            return jobDefault ?: archetypeDefault
        }


        // The Worker's OWN (explicit override) `channels.<outputPort>.<knob>` value — not inheritance-resolved,
        // so an unset Worker reads null rather than any archetype value — or null when unset / blank. Drives the
        // collapsed caption (which lists only overridden knobs) and the customized cue.
        fun ownChannelValue(
            graphNotation: GraphNotation,
            workerLocation: ObjectLocation,
            outputPort: AttributeName,
            knob: AttributeName
        ): String? {
            val workerNotation = graphNotation.documents[workerLocation.documentPath]
                ?.objects?.notations?.map?.get(workerLocation.objectPath)
                ?: return null
            return workerNotation.get(JobConventions.workerOutputKnobPath(outputPort, knob))
                ?.asString()?.ifBlank { null }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        if (props.expanded) {
            renderExpanded()
        }
        else {
            renderCollapsed()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The COLLAPSED gold chevron pointing down into the Worker card below it, marking the channel between two
    // adjacent Workers. When customized, a caption below the chevron shows the override values so they're
    // visible without expanding.
    private fun ChildrenBuilder.renderCollapsed() {
        // Only the EXPLICITLY overridden knobs are summarized; an inherited (defaulted) knob is omitted. A
        // channel is customized when at least one knob is overridden.
        val overrides = buildList {
            props.batchSizeOverride?.let { add("batch size = $it") }
            props.capacityOverride?.let { add("capacity = $it") }
        }
        val customized = overrides.isNotEmpty()

        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = AlignItems.center
                width = 100.pct
                marginBottom = JobChannelDisplayStyle.marginBottom
            }

            div {
                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                    justifyContent = JustifyContent.center
                    width = 2.4.em
                    height = 1.9.em
                    paddingBottom = 12.px
                    clipPath = JobChannelDisplayStyle.downwardChevron
                    // Customized → bold gold; inheriting the defaults → pale, so a tuned channel stands out.
                    if (customized) {
                        backgroundColor = JobChannelDisplayStyle.border
                        color = NamedColor.white
                    }
                    else {
                        backgroundColor = JobChannelDisplayStyle.fill
                        color = JobChannelDisplayStyle.accent
                    }
                    cursor = Cursor.pointer
                }
                title = "${props.upstreamName} → ${props.downstreamName} " +
                        "(batch size ${props.batchSize}, capacity ${props.capacity}) — " +
                        if (customized) "customized; click to edit" else "click to customize"

                onClick = {
                    props.onToggle(props.upstreamWorker)
                }

                icon("material-symbols:keyboard-arrow-down") {}
            }

            // The overridden values, visible while collapsed so a customized channel doesn't have to be opened
            // to read what it overrides (defaulted knobs are omitted).
            if (customized) {
                div {
                    css {
                        fontSize = 0.7.em
                        color = JobChannelDisplayStyle.accent
                        whiteSpace = WhiteSpace.nowrap
                        cursor = Cursor.pointer
                    }
                    onClick = {
                        props.onToggle(props.upstreamWorker)
                    }
                    +overrides.joinToString(" · ")
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The EXPANDED inline editor: a downward-chevron card (an `upstream →
    // downstream` header + Delete / Collapse buttons + the two batchSize / capacity fields) that edits the
    // UPSTREAM Worker's per-output config (`channels.<outputPort>`) directly. Clicking an ambient area of the
    // card (its padding / the header route text) collapses it back to the compact chevron — the same
    // click-to-collapse affordance as a Script step; the two config fields sit in a click-swallowing wrapper so
    // clicking into them to type never collapses the card.
    private fun ChildrenBuilder.renderExpanded() {
        val workerLocation = props.upstreamWorker
        val outputPort = props.outputPort
        val routeTitle = props.upstreamName + " → " + props.downstreamName

        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                width = 100.pct
                marginBottom = JobChannelDisplayStyle.marginBottom
            }

            div {
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.column
                    gap = 0.5.em
                    // Extra bottom padding clears the chevron's downward point (see downwardChevron).
                    padding = Padding(0.5.em, 0.75.em, 1.1.em, 0.75.em)
                    clipPath = JobChannelDisplayStyle.downwardChevron
                    backgroundColor = JobChannelDisplayStyle.fill
                    color = JobChannelDisplayStyle.accent
                    minWidth = 14.em
                    cursor = Cursor.pointer
                }

                // Ambient click (padding / header text) collapses the editor, mirroring a Script step.
                onClick = {
                    props.onToggle(workerLocation)
                }

                div {
                    css {
                        display = Display.flex
                        alignItems = AlignItems.center
                        justifyContent = JustifyContent.spaceBetween
                        gap = 0.5.em
                    }

                    div {
                        css {
                            fontSize = 0.9.em
                            fontWeight = FontWeight.bold
                        }
                        +routeTitle
                    }

                    div {
                        css {
                            display = Display.flex
                            alignItems = AlignItems.center
                        }

                        // Delete the customization → remove the whole `channels.<port>` entry → the channel
                        // reverts to the Job-wide default (the fields fall back to showing it).
                        IconButton {
                            title = "Delete override — revert to the Job-wide default"
                            size = Size.small
                            onClick = {
                                it.stopPropagation()
                                props.onClear(workerLocation, outputPort)
                            }
                            icon("material-symbols:delete") {}
                        }

                        // Collapse the editor back to the compact chevron (keeps any customization).
                        IconButton {
                            title = "Collapse"
                            size = Size.small
                            onClick = {
                                it.stopPropagation()
                                props.onToggle(workerLocation)
                            }
                            icon("material-symbols:keyboard-arrow-up") {}
                        }
                    }
                }

                // The config fields swallow clicks so clicking into a field to type never bubbles up to the
                // card's collapse handler.
                div {
                    css {
                        display = Display.flex
                        flexDirection = FlexDirection.column
                        gap = 0.5.em
                        cursor = Cursor.default
                    }
                    onClick = {
                        it.stopPropagation()
                    }

                    JobChannelNumberField::class.react {
                        label = "Batch size"
                        objectLocation = workerLocation
                        attributePath = JobConventions.workerOutputKnobPath(
                            outputPort, JobConventions.batchSizeAttributeName)
                        fallbackValue = props.batchSizeFallback
                        clientStateGlobal = props.clientStateGlobal
                        mirroredGraphStore = props.mirroredGraphStore
                    }

                    JobChannelNumberField::class.react {
                        label = "Capacity"
                        objectLocation = workerLocation
                        attributePath = JobConventions.workerOutputKnobPath(
                            outputPort, JobConventions.capacityAttributeName)
                        fallbackValue = props.capacityFallback
                        clientStateGlobal = props.clientStateGlobal
                        mirroredGraphStore = props.mirroredGraphStore
                    }
                }
            }
        }
    }
}
