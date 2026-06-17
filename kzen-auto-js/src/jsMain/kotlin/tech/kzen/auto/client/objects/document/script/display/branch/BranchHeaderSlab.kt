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
import web.cssom.NamedColor
import web.cssom.Overflow
import web.cssom.Padding
import web.cssom.Transition
import web.cssom.em
import web.cssom.px


// Shared white-card scaffold for branch-bearing steps (If, Mapping): a coloured-background header
// row over an attribute-editor row, both wrapped in a white card with bottom padding so the next
// branch slab meets it flush.
fun ChildrenBuilder.branchHeaderSlab(
    objectLocation: ObjectLocation,
    icon: String,
    description: String,
    title: String,
    trace: StepTrace?,
    isNextToRun: Boolean,
    mirroredGraphStore: MirroredGraphStore,
    body: ChildrenBuilder.() -> Unit
) {
    val traceState = trace?.state ?: StepTrace.State.Idle

    div {
        css {
            backgroundColor = NamedColor.white
            paddingBottom = 0.5.em

            // Soft elevation matching the leaf step cards (shared tokens). Only the TOP corners are
            // rounded — the bottom stays square (crisp), since that edge is where the recessed-stage
            // down-shadow onto the branch below originates and the white trunk descends from it.
            // overflow:hidden clips the inner status-coloured header background to the rounded top
            // corners (so a running/done tint doesn't poke past them); the card's own box-shadow is
            // painted outside the box and is unaffected by the clip.
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
                backgroundColor = ScriptStepDisplayDefault.backgroundColor(traceState, trace?.error, isNextToRun)
            }

            StepHeader::class.react {
                this.objectLocation = objectLocation
                managed = false
                this.icon = icon
                this.description = description
                this.title = title
                this.mirroredGraphStore = mirroredGraphStore
            }
        }

        div {
            css {
                padding = Padding(0.em, 1.em, 0.em, 1.em)
            }
            body()
        }
    }
}
