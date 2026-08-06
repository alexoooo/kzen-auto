package tech.kzen.auto.client.objects.document.script.display.branch

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayDefault
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


// Shared white-card scaffold for branch-bearing steps (If, ForEach, DoWhile): a header row over an
// optional attribute-editor row, wrapped in a white card with a left status colour bar (matching leaf
// steps, rather than a filled header) and bottom padding so the next branch slab meets it flush.
//
// [body] is null where the construct's editors belong to its sections rather than to its title: DoWhile tests
// AFTER its branch runs, so its editor sits in the footer below the stage; an If chain has one condition per
// branch, each in its own branchSectionSlab.
fun ChildrenBuilder.branchHeaderSlab(
    objectLocation: ObjectLocation,
    icon: String,
    description: String,
    title: String,
    trace: StepTrace?,
    isNextToRun: Boolean,
    mirroredGraphStore: MirroredGraphStore,
    typeMetadata: String? = null,
    validationError: String? = null,
    validationWarning: String? = null,
    isResult: Boolean = false,
    partial: Boolean = false,
    body: (ChildrenBuilder.() -> Unit)? = null
) {
    val traceState = trace?.state ?: StepTrace.State.Idle

    div {
        css {
            backgroundColor = NamedColor.white
            paddingBottom = 0.5.em

            // Status shown as a left colour bar (gold next-to-run / running, green done, red error),
            // consistent with the leaf step cards — instead of filling the header background. 4px is
            // kept even when idle (white) so there's no layout shift on state change.
            borderLeftWidth = ScriptStepDisplayDefault.statusBorderWidth
            borderLeftStyle = LineStyle.solid
            borderLeftColor = ScriptStepDisplayDefault.statusBorderColor(
                traceState, trace?.error, isNextToRun, validationError, validationWarning)

            // Soft elevation matching the leaf step cards (shared tokens). Only the TOP corners are
            // rounded — the bottom stays square (crisp), since that edge is where the recessed-stage
            // down-shadow onto the branch below originates.
            // overflow:hidden clips inner content to the rounded top corners; the card's own
            // box-shadow is painted outside the box and is unaffected by the clip.
            borderTopLeftRadius = ScriptStepDisplayDefault.cardCornerRadius
            borderTopRightRadius = ScriptStepDisplayDefault.cardCornerRadius
            overflow = Overflow.hidden
            boxShadow = ScriptStepDisplayDefault.cardRestingShadow
            transition = "box-shadow 120ms ease-out".unsafeCast<Transition>()
            "&:hover" {
                boxShadow = ScriptStepDisplayDefault.cardHoverShadow
            }
        }

        div {
            css {
                padding = Padding(16.px, 16.px, 0.px, 16.px)
            }

            StepHeader::class.react {
                this.objectLocation = objectLocation
                managed = false
                this.icon = icon
                this.description = description
                this.title = title
                this.typeMetadata = typeMetadata
                this.validationError = validationError
                this.validationWarning = validationWarning
                this.isResult = isResult
                this.skipped = traceState == StepTrace.State.Skipped
                this.partial = partial
                this.mirroredGraphStore = mirroredGraphStore
            }
        }

        if (body != null) {
            div {
                css {
                    padding = Padding(0.em, 1.em, 0.em, 1.em)
                }
                body()
            }
        }
    }
}
