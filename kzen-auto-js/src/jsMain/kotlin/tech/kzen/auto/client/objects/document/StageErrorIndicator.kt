package tech.kzen.auto.client.objects.document

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Popover
import mui.material.PopoverOrigin
import react.ChildrenBuilder
import react.Key
import react.Props
import react.RefObject
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.util.DefinitionErrors
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.AlignItems
import web.cssom.BoxShadow
import web.cssom.Color
import web.cssom.Cursor
import web.cssom.Display
import web.cssom.FontWeight
import web.cssom.Length
import web.cssom.LineStyle
import web.cssom.NamedColor
import web.cssom.OverflowWrap
import web.cssom.Position
import web.cssom.em
import web.cssom.integer
import web.cssom.px
import web.html.HTMLElement


//---------------------------------------------------------------------------------------------------------------------
external interface StageErrorIndicatorProps: Props {
    // Definition failures in the currently-open document (the actionable set, highlighted per-field below).
    var documentErrors: List<DefinitionErrors.Line>

    // Definition failures in every OTHER document — surfaced as a secondary popover section so a cross-document
    // break isn't lost when the whole-notation banner is gone.
    var otherErrors: List<DefinitionErrors.Line>

    // Top of the stage (== header height), from StageController.StageContext; the chip pins just below it.
    var stageTop: Length

    // Invoked with the line's object when it is clicked, to bring that object into view (StageObjectLocator).
    // Must be a stable reference from the owner — a fresh closure per render would defeat this component's
    // shallow-compare bail-out.
    var onSelectLocation: (ObjectLocation) -> Unit
}


external interface StageErrorIndicatorState: State {
    var open: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
// A compact notation-error indicator pinned to the stage's top-right, replacing the two full-width red banners
// that pushed the document layout down. Paradigm-generic (owned by StageController, so Script/Job/Flow/Report all
// get it). The chip is out-of-flow (position: fixed) so it never shifts the page body; on click it expands into a
// popover listing this document's failures and, secondarily, other documents'.
//
// NB: this component is ALWAYS mounted by StageController (it renders null when there are no errors), so its
//     presence never perturbs the sibling index of the document body rendered after it — see the remount caution
//     in StageController.errorPanel's doc comment. Do NOT let StageController render it conditionally.
class StageErrorIndicator(
    props: StageErrorIndicatorProps
):
    RPureComponent<StageErrorIndicatorProps, StageErrorIndicatorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Matches the validation-error accent used on step cards / attribute fields — a red-orange, distinct from
        // the darker run-failure red.
        private val errorColour = Color("#d84315")

        // Muted tone when the current document is clean but another document has a failure.
        private val mutedColour = Color("gray")

        // Neutral row highlight on the popover's white surface — a red tint would read as a severity change.
        private val hoverColour = Color("#eeeeee")

        // Vertical space (em) this chip occupies at the stage's top-right, including its margin. Every float that
        // shares this corner — Parameters (LogicSignatureEditor), Result (ResultSignatureEditor), and a Job's
        // Channel defaults (JobChannelDefaults) — adds this to its own stack offset so it always clears the chip.
        //
        // Reserved UNCONDITIONALLY rather than toggled on error presence, for two reasons: the layout must not
        // shift as errors appear/clear, and the chip's condition (definition failures OR async validation errors)
        // isn't observable from those floats — a conditional offset silently desynced and overlapped. Defined here,
        // once, because three separate floats previously hardcoded their own offsets and drifted apart.
        const val reservedRowEm = 2.5
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val anchorRef: RefObject<HTMLElement> = createRef()


    //-----------------------------------------------------------------------------------------------------------------
    override fun StageErrorIndicatorState.init(props: StageErrorIndicatorProps) {
        open = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    // NB: the toggle is computed OUTSIDE setState. Its lambda builds a fresh, EMPTY partial-state object (see
    // wrap/React.kt), so `!open` in there negates undefined — always true, leaving the chip unable to close
    // itself once open (only click-away worked).
    private fun toggleOpen() {
        val toggled = !state.open
        setState {
            open = toggled
        }
    }


    private fun close() {
        setState {
            open = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val documentErrors = props.documentErrors
        val otherErrors = props.otherErrors
        if (documentErrors.isEmpty() && otherErrors.isEmpty()) {
            return
        }

        val currentColour = if (documentErrors.isNotEmpty()) errorColour else mutedColour

        div {
            css {
                position = Position.fixed
                top = props.stageTop
                right = 0.px
                // above the step cards and the Parameters/Result floats (zIndex 2); the popover portals above all.
                zIndex = integer(3)
            }

            renderChip(currentColour, documentErrors, otherErrors)
            renderPopover(documentErrors, otherErrors)
        }
    }


    private fun ChildrenBuilder.renderChip(
        currentColour: Color,
        documentErrors: List<DefinitionErrors.Line>,
        otherErrors: List<DefinitionErrors.Line>
    ) {
        div {
            ref = anchorRef

            css {
                margin = 0.5.em
                display = Display.flex
                alignItems = AlignItems.center
                cursor = Cursor.pointer

                backgroundColor = NamedColor.white
                borderWidth = 1.px
                borderStyle = LineStyle.solid
                borderColor = currentColour
                borderRadius = 4.px
                paddingTop = 0.1.em
                paddingBottom = 0.1.em
                paddingLeft = 0.4.em
                paddingRight = 0.2.em
                color = currentColour
                boxShadow = "0 1px 4px rgba(0, 0, 0, 0.2)".unsafeCast<BoxShadow>()
            }

            onClick = { toggleOpen() }

            icon("material-symbols:error") {
                style = unsafeJso {
                    color = currentColour
                    fontSize = 1.1.em
                }
            }

            span {
                css {
                    marginLeft = 0.25.em
                    fontSize = 0.8.em
                }
                +summaryLabel(documentErrors, otherErrors)
            }

            icon(if (state.open) "material-symbols:expand-less" else "material-symbols:expand-more") {
                style = unsafeJso {
                    color = currentColour
                }
            }
        }
    }


    private fun summaryLabel(
        documentErrors: List<DefinitionErrors.Line>,
        otherErrors: List<DefinitionErrors.Line>
    ): String {
        if (documentErrors.isNotEmpty()) {
            val count = documentErrors.size
            return if (count == 1) "1 error" else "$count errors"
        }

        val otherDocumentCount = otherErrors.map { it.location.documentPath }.distinct().size
        return if (otherDocumentCount == 1) "error in another document" else "errors in $otherDocumentCount documents"
    }


    private fun ChildrenBuilder.renderPopover(
        documentErrors: List<DefinitionErrors.Line>,
        otherErrors: List<DefinitionErrors.Line>
    ) {
        val bottomRight: PopoverOrigin = unsafeJso {
            asDynamic().vertical = "bottom"
            asDynamic().horizontal = "right"
        }
        val topRight: PopoverOrigin = unsafeJso {
            asDynamic().vertical = "top"
            asDynamic().horizontal = "right"
        }

        Popover {
            open = state.open
            onClose = { _, _ -> close() }
            anchorEl = anchorRef.current
            anchorOrigin = bottomRight
            transformOrigin = topRight

            div {
                css {
                    padding = 0.75.em
                    maxWidth = 40.em
                    fontSize = 0.85.em
                }

                if (documentErrors.isNotEmpty()) {
                    renderSection("This document") {
                        for ((location, detail) in documentErrors) {
                            // Keyed by location AND detail: the merged list can hold several validation findings on
                            // the same object (e.g. a Flow's structure findings all tied to its main object), so
                            // location alone would collide.
                            renderLine(
                                key = "${location.asString()}|$detail",
                                location = location,
                                text = "${location.objectPath.name.value} — $detail")
                        }
                    }
                }

                if (otherErrors.isNotEmpty()) {
                    val otherDocumentCount = otherErrors.map { it.location.documentPath }.distinct().size
                    renderSection("Other documents ($otherDocumentCount)") {
                        for (line in otherErrors) {
                            renderLine(
                                key = "${line.location.asString()}|${line.detail}",
                                location = line.location,
                                text = "${line.location.asString()} — ${line.detail}")
                        }
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderSection(heading: String, body: ChildrenBuilder.() -> Unit) {
        div {
            css {
                marginTop = 0.5.em
                fontWeight = FontWeight.bold
                color = errorColour
            }
            +heading
        }
        body()
    }


    private fun ChildrenBuilder.renderLine(key: String, location: ObjectLocation, text: String) {
        div {
            this.key = Key(key)
            css {
                marginTop = 0.25.em
                paddingTop = 0.1.em
                paddingBottom = 0.1.em
                overflowWrap = OverflowWrap.anywhere
                cursor = Cursor.pointer
                borderRadius = 2.px

                "&:hover" {
                    backgroundColor = hoverColour
                }
            }

            // The "This document" section shows only the object's name, so the full location is worth surfacing;
            // stating the action makes the row's clickability discoverable in both sections.
            title = "Go to ${location.asString()}"

            // Closed first so the popover isn't covering the object it's about to scroll to.
            onClick = {
                close()
                props.onSelectLocation(location)
            }

            +text
        }
    }
}
