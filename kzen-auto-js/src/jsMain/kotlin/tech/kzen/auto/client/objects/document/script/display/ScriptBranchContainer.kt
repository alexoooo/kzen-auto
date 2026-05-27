package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.wrap.react
import tech.kzen.lib.common.model.location.AttributeLocation
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
// NB: shared branch-row layout for IfStep (Then/Else) and MappingStep (Each). Renders a narrow
//     white "indent" column on the left holding just the label, paired with the branch's step list
//     on the right. The strip stretches to the row's full height via flex `alignItems = stretch`
//     plus 28px vertical padding — so the strip's white bg covers the right column's top/bottom
//     28px placeholder reservations on the LEFT 4em, and adjacent branches' strips meet flush
//     forming one continuous F-shape trunk that extends to the bottom of the last branch's row.
//
//     The right column is deliberately transparent (page gray shows through around the steps) so
//     each step card reads as a discrete white card on gray, not as part of one solid slab.
//
//     Top decorations (full-width seam line, white middle arm) are rendered by the caller
//     (IfStepDisplay), not by this helper — different branches want different top visuals.
fun ChildrenBuilder.scriptBranchContainer(
    label: String,
    branchLocation: AttributeLocation,
    stepDisplayManager: StepDisplayManager.Wrapper,
    scriptCommander: ScriptCommander
) {
    div {
        css {
            display = Display.flex
            alignItems = AlignItems.stretch
        }

        div {
            css {
                backgroundColor = NamedColor.white
                width = 4.em
                flexShrink = number(0.0)
                padding = Padding(28.px, 0.75.em)
                color = Color("rgba(0, 0, 0, 0.7)")
            }
            +label
        }

        div {
            css {
                flexGrow = number(1.0)
                marginLeft = 0.5.em
                minHeight = 4.em
            }

            ScriptBranchDisplay::class.react {
                attributeLocation = branchLocation
                nested = true
                this.stepDisplayManager = stepDisplayManager
                this.scriptCommander = scriptCommander
            }
        }
    }
}
