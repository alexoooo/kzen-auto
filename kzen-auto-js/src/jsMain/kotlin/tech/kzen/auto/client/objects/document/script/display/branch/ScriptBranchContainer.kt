package tech.kzen.auto.client.objects.document.script.display.branch

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayDefault
import tech.kzen.auto.client.objects.document.script.display.StepDisplayManager
import tech.kzen.auto.client.objects.document.script.display.dependency.ScriptBranchDisplay
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.react
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
// NB: shared branch-row layout for IfStep (Then/Else) and ForEachStep (Each). Renders a narrow
//     white "indent" column on the left holding just the label, paired with the branch's step list
//     on the right. The strip stretches to the row's full height via flex `alignItems = stretch`
//     plus 32px vertical padding — so the strip's white bg covers the right column's top/bottom
//     32px placeholder reservations on the LEFT 4em, and adjacent branches' strips meet flush
//     forming one continuous F-shape trunk that extends to the bottom of the last branch's row.
//
//     The right column is deliberately transparent (page gray shows through around the steps) so
//     each step card reads as a discrete white card on gray, not as part of one solid slab.
//
//     Top/edge decorations (seam line, down-shadow, vertical ledge) are rendered by the caller
//     (IfStepDisplay / ForEachStepDisplay) via the shared branchStage* helpers in
//     BranchStageChrome.kt — not by this helper, since branches differ in their top visuals
//     (e.g. the If's Then branch adds a bottom fade-to-white lip the others don't).
fun ChildrenBuilder.scriptBranchContainer(
    label: String,
    branchLocation: AttributeLocation,
    stepDisplayManager: StepDisplayManager.Wrapper,
    scriptCommander: ScriptCommander,
    roundedBottom: Boolean,
    clientStateGlobal: ClientStateGlobal,
    mirroredGraphStore: MirroredGraphStore,
    objectStableMapper: ObjectStableMapper
) {
    div {
        css {
            display = Display.flex
            alignItems = AlignItems.stretch
        }

        div {
            css {
                backgroundColor = NamedColor.white
                // Trunk content width — sized to the short branch labels ("Then"/"Else"/"Each") with
                // minimal slack. Content-box, so the outer right edge is width + 2×0.75em padding;
                // IfStepDisplay's vertical ledge line is positioned to that outer edge (keep in sync).
                width = 3.em
                flexShrink = number(0.0)
                padding = Padding(32.px, 0.75.em)
                color = Color("rgba(0, 0, 0, 0.7)")

                // Last branch of the construct: round the trunk's bottom corners so its white fill
                // clips to the rounded shape (border-radius clips the background) — the white "⌐"
                // frame's bottom. branchStageBase draws the matching hairline + shade over this edge.
                if (roundedBottom) {
                    borderBottomLeftRadius = ScriptStepDisplayDefault.cardCornerRadius
                    borderBottomRightRadius = ScriptStepDisplayDefault.cardCornerRadius
                }
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
                this.clientStateGlobal = clientStateGlobal
                this.mirroredGraphStore = mirroredGraphStore
                this.objectStableMapper = objectStableMapper
            }
        }
    }
}
