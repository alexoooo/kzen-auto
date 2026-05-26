package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import js.objects.unsafeJso
import react.ChildrenBuilder
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.wrap.material.iconByName
import tech.kzen.auto.client.wrap.react
import tech.kzen.lib.common.model.location.AttributeLocation
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
val scriptBranchOverlapTop = 4.px


//---------------------------------------------------------------------------------------------------------------------
// NB: shared layout for IfStep.then / IfStep.else / MappingStep.steps branch containers.
// The Else variant uses doubled outer marginBottom (it's the last row of the IfStep table) and a
// full-width inline-block label; both are explicit parameters to preserve their long-standing
// rendering rather than normalize them.
fun ChildrenBuilder.scriptBranchContainer(
    label: String,
    branchLocation: AttributeLocation,
    stepDisplayManager: StepDisplayManager.Wrapper,
    scriptCommander: ScriptCommander,
    outerMarginBottom: Length = scriptBranchOverlapTop,
    labelFullWidth: Boolean = false
) {
    div {
        css {
            width = 100.pct
            marginBottom = outerMarginBottom
        }

        div {
            css {
                if (labelFullWidth) {
                    width = 100.pct
                }
                display = Display.inlineBlock
                marginLeft = 3.px
            }

            +label
            br {}
            iconByName("ArrowForward") {
                style = unsafeJso {
                    fontSize = 3.em
                }
            }
        }

        div {
            css {
                width = 100.pct.minus(3.em)
                display = Display.inlineBlock
                marginTop = (-4.5).em
                marginLeft = 3.5.em
            }

            ScriptBranchDisplay::class.react {
                attributeLocation = branchLocation
                nested = true
                this.stepDisplayManager = stepDisplayManager
                this.scriptCommander = scriptCommander
            }
        }

        div {
            iconByName("SubdirectoryArrowLeft") {
                style = unsafeJso {
                    fontSize = 3.em
                    marginBottom = 15.px
                    marginTop = (-40).px
                }
            }
        }
    }
}
