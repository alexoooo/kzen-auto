package tech.kzen.auto.client.objects.document.script.display.branch

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayDefault
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.NamedColor
import web.cssom.Padding
import web.cssom.em
import web.cssom.px


// Shared white-card scaffold for branch-bearing steps (If, Mapping): a coloured-background header
// row over an attribute-editor row, both wrapped in a white card with bottom padding so the next
// branch slab meets it flush.
fun ChildrenBuilder.branchHeaderSlab(
    indexInParent: Int,
    objectLocation: ObjectLocation,
    icon: String,
    description: String,
    title: String,
    trace: StepTrace?,
    isNextToRun: Boolean,
    body: ChildrenBuilder.() -> Unit
) {
    val traceState = trace?.state ?: StepTrace.State.Idle

    div {
        css {
            backgroundColor = NamedColor.white
            paddingBottom = 0.5.em
        }

        div {
            css {
                padding = Padding(16.px, 16.px, 0.px, 16.px)
                backgroundColor = ScriptStepDisplayDefault.backgroundColor(traceState, trace?.error, isNextToRun)
            }

            StepHeader::class.react {
                this.indexInParent = indexInParent
                this.objectLocation = objectLocation
                managed = false
                this.icon = icon
                this.description = description
                this.title = title
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
