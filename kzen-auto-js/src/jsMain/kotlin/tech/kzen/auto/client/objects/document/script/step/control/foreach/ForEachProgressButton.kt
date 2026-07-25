package tech.kzen.auto.client.objects.document.script.step.control.foreach

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
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.model.ForEachProgress
import web.cssom.*
import web.html.HTMLElement


//---------------------------------------------------------------------------------------------------------------------
external interface ForEachProgressButtonProps: Props {
    var progress: ForEachProgress
}


external interface ForEachProgressButtonState: State {
    var open: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * The loop's iteration counter, at the right edge of the ForEach's item row, and the journal it opens: the values
 * the body has produced so far, one row per completed iteration.
 *
 * A component rather than a render function purely because the popover needs open/anchor state, which
 * [forEachItemRow] — a plain [ChildrenBuilder] extension — has nowhere to hold. Its props change every progress
 * tick (a fresh [ForEachProgress] per parse), so it re-renders while open, which is the point: the journal fills
 * in live as the loop runs.
 */
class ForEachProgressButton(
    props: ForEachProgressButtonProps
):
    RPureComponent<ForEachProgressButtonProps, ForEachProgressButtonState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val borderColour = Color("#bdbdbd")
        private val mutedColour = Color("gray")
        private val hoverColour = Color("#eeeeee")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val anchorRef: RefObject<HTMLElement> = createRef()


    //-----------------------------------------------------------------------------------------------------------------
    override fun ForEachProgressButtonState.init(props: ForEachProgressButtonProps) {
        open = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun close() {
        setState {
            open = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        renderCounter()
        renderPopover()
    }


    private fun ChildrenBuilder.renderCounter() {
        val progress = props.progress

        div {
            ref = anchorRef

            css {
                flexShrink = number(0.0)
                marginLeft = 0.5.em
                display = Display.flex
                alignItems = AlignItems.center
                cursor = Cursor.pointer

                fontSize = 0.8.em
                color = mutedColour
                backgroundColor = NamedColor.white
                borderWidth = 1.px
                borderStyle = LineStyle.solid
                borderColor = borderColour
                borderRadius = 1.em
                padding = Padding(0.1.em, 0.6.em)

                "&:hover" {
                    backgroundColor = hoverColour
                }
            }

            title = "Values produced so far"

            // stopPropagation: the enclosing step card owns click-to-toggle, so a control that handles its own
            // click must not also expand the card (the convention StepHeader's buttons follow).
            //
            // NB: the toggle is computed OUTSIDE setState — its lambda runs against an empty partial-state
            // object, so reading `open` in there would read undefined rather than the current value.
            onClick = {
                it.stopPropagation()
                val toggled = !state.open
                setState {
                    open = toggled
                }
            }

            +counterLabel(progress)
        }
    }


    // "2 of 5" while a sized collection runs, bare "2" when the items value is a plain Iterable with no known size.
    private fun counterLabel(progress: ForEachProgress): String {
        val position = progress.index + 1
        val size = progress.size
            ?: return "$position"
        return "$position of $size"
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderPopover() {
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

                // The panel's own click must not reach the step card behind it — the popover portals out of the
                // card's DOM subtree, but React's synthetic events still bubble along the component tree.
                onClick = { it.stopPropagation() }

                renderJournal()
            }
        }
    }


    private fun ChildrenBuilder.renderJournal() {
        val progress = props.progress

        div {
            css {
                display = Display.flex
                alignItems = AlignItems.baseline
                fontWeight = FontWeight.bold
                marginBottom = 0.5.em
            }

            +"Values produced"

            span {
                css {
                    marginLeft = 0.75.em
                    fontWeight = FontWeight.normal
                    color = mutedColour
                }
                +"${progress.producedCount}"
            }
        }

        if (progress.produced.isEmpty()) {
            div {
                css {
                    color = mutedColour
                }
                +"No values yet"
            }
            return
        }

        val omitted = progress.omittedCount()
        if (omitted > 0) {
            div {
                css {
                    color = mutedColour
                    marginBottom = 0.25.em
                }
                +"… $omitted earlier omitted"
            }
        }

        div {
            css {
                // Bounded height so a full (capped at ForEachProgress.maxProducedEntries) journal scrolls
                // inside the popover rather than growing it past the viewport.
                maxHeight = 24.em
                overflowY = Auto.auto
            }

            for ((offset, entry) in progress.produced.withIndex()) {
                renderEntry(omitted + offset + 1, entry)
            }
        }
    }


    private fun ChildrenBuilder.renderEntry(position: Int, entry: ForEachProgress.Entry) {
        div {
            key = Key("$position")

            css {
                display = Display.flex
                alignItems = AlignItems.baseline
                paddingTop = 0.15.em
                paddingBottom = 0.15.em
                borderRadius = 2.px

                "&:hover" {
                    backgroundColor = hoverColour
                }
            }

            span {
                css {
                    flexShrink = number(0.0)
                    minWidth = 2.5.em
                    textAlign = TextAlign.right
                    marginRight = 0.75.em
                    color = mutedColour
                }
                +"$position"
            }

            span {
                css {
                    overflowWrap = OverflowWrap.anywhere
                    color = mutedColour
                }
                +entry.item
            }

            span {
                css {
                    flexShrink = number(0.0)
                    marginLeft = 0.5.em
                    marginRight = 0.5.em
                    color = mutedColour
                }
                +"→"
            }

            span {
                css {
                    overflowWrap = OverflowWrap.anywhere
                    fontWeight = FontWeight.bold
                }
                +entry.value
            }
        }
    }
}
