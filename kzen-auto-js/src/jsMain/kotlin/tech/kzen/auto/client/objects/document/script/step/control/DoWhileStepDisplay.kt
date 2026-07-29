package tech.kzen.auto.client.objects.document.script.step.control

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.display.*
import tech.kzen.auto.client.objects.document.script.display.branch.*
import tech.kzen.auto.client.objects.document.script.display.dependency.ScriptBranchDisplay
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class DoWhileStepDisplay(
    props: BranchStepDisplayProps
):
    ScriptStepDisplayBase<BranchStepDisplayProps, ScriptStepDisplayBaseState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Hairline separating the white While footer from the gray body stage above it — the closing
        // mirror of the stage's opening branchStageSeam. Drawn as the footer's own border-top rather than
        // as a seam element on the stage: sitting on the footer's white, 12% black reads at the intended
        // hairline weight, where the same colour on the gray stage would come out markedly darker. Its
        // width is what the rail's band overhangs to cover the border's left miter.
        private const val bodySeamWidthPx = 1
        private val bodySeamWidth = bodySeamWidthPx.px
        private val bodySeamColor = Color("rgba(0, 0, 0, 0.12)")

        // Footer content inset, matching branchHeaderSlab's header padding: both sit inside a status bar
        // of the same width, so header title, "While" label and the branchRailWidth-indented body all
        // share one left column.
        private val footerInset = 16.px
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        private val stepDisplayManager: StepDisplayManager.Handle,
        private val scriptCommander: ScriptCommander,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val objectStableMapper: ObjectStableMapper,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        ScriptStepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: ScriptStepDisplayProps.() -> Unit) {
            DoWhileStepDisplay::class.react {
                attributeEditorManager = this@Wrapper.attributeEditorManager
                stepDisplayManager = this@Wrapper.stepDisplayManager.wrapper!!
                scriptCommander = this@Wrapper.scriptCommander
                clientStateGlobal = this@Wrapper.clientStateGlobal
                objectStableMapper = this@Wrapper.objectStableMapper
                mirroredGraphStore = this@Wrapper.mirroredGraphStore

                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        // The header slab derives this same bar from the trace it is passed; the stage's band and the footer's
        // border take it from here, so all three segments of the construct's left edge match.
        val trace = state.stepTrace
        val accent = ScriptStepDisplayDefault.statusBorderColor(
            trace?.state ?: StepTrace.State.Idle,
            trace?.error,
            state.isNextToRun ?: false,
            state.stepValidation?.errorMessage,
            state.stepValidation?.warningMessage)

        // Three flush sections, ordered to match the do-while (run the body, THEN test the condition):
        //   header slab (white) → body steps (recessed gray stage) → full-width "While" footer.
        // The status bar runs the height of all three, so they read as one card.
        renderHeader()
        renderBodyStage(accent)
        renderWhileFooter(accent)
    }


    // The same white title slab If and ForEach open with — minus its attribute-editor row: a do-while
    // tests AFTER its body runs, so the condition editor belongs in the While footer, not up here.
    private fun ChildrenBuilder.renderHeader() {
        branchHeaderSlab(
            objectLocation = props.common.objectLocation,
            icon = state.icon ?: "",
            description = state.description ?: "",
            title = state.title ?: "",
            trace = state.stepTrace,
            isNextToRun = state.isNextToRun ?: false,
            mirroredGraphStore = props.mirroredGraphStore,
            typeMetadata = state.stepValidation?.typeMetadata?.toSimple(),
            validationError = state.stepValidation?.errorMessage,
            validationWarning = state.stepValidation?.warningMessage)
    }


    // The loop body: the step list on the recessed gray stage. The rail does not fade at its bottom here,
    // since the header slab above and the While footer below bracket this stage between two white slabs.
    private fun ChildrenBuilder.renderBodyStage(accent: Color) {
        div {
            css {
                position = Position.relative
            }

            // The band overhangs onto the While footer's border-top, covering the wedge that border's
            // miter would otherwise cut across the status bar — so the construct's left edge stays one
            // unbroken bar through all three sections. Only the band overhangs; the footer's hairline is
            // untouched everywhere right of it.
            branchStageAccentRail(accent, fadeBottom = false, bandOverhangBottomPx = bodySeamWidthPx)

            // Seam + down-shadow cast by the header slab above.
            branchStageSeam()
            branchStageTopShadow {
                div {
                    css {
                        // The rail is paint only (absolutely positioned over the stage's left edge), so
                        // this indent is the only thing holding the step rows off the scope line.
                        marginLeft = branchRailWidth
                        minHeight = 4.em
                    }

                    ScriptBranchDisplay::class.react {
                        attributeLocation = AttributeLocation(
                            props.common.objectLocation, ScriptConventions.stepsAttributePath)
                        nested = true
                        stepDisplayManager = props.stepDisplayManager
                        scriptCommander = props.scriptCommander
                        clientStateGlobal = props.clientStateGlobal
                        mirroredGraphStore = props.mirroredGraphStore
                        objectStableMapper = props.objectStableMapper
                    }
                }
            }
        }
    }


    // The "While" condition: a full-width white footer (NOT a branch — no step list, no gray stage).
    // The "While" label leads the row at the header's own inset, closing the body the way the header
    // slab opens it; the Kotlin Boolean editor extends horizontally across the rest. Rounded bottom
    // completes the construct's frame.
    private fun ChildrenBuilder.renderWhileFooter(accent: Color) {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                backgroundColor = NamedColor.white
                borderLeftWidth = ScriptStepDisplayDefault.statusBorderWidth
                borderLeftStyle = LineStyle.solid
                borderLeftColor = accent
                borderTop = Border(bodySeamWidth, LineStyle.solid, bodySeamColor)
                borderBottomLeftRadius = ScriptStepDisplayDefault.cardCornerRadius
                borderBottomRightRadius = ScriptStepDisplayDefault.cardCornerRadius
                boxShadow = ScriptStepDisplayDefault.cardRestingShadow
            }

            // "While" label, sized to its own text and centred against the editor beside it by the row's
            // alignItems.
            div {
                css {
                    flexShrink = number(0.0)
                    padding = Padding(0.px, 0.75.em, 0.px, footerInset)
                    color = Color("rgba(0, 0, 0, 0.7)")
                }
                +"While"
            }

            // The condition editor, extending horizontally.
            div {
                css {
                    flexGrow = number(1.0)
                    minWidth = 0.px
                    padding = Padding(12.px, 16.px, 12.px, 0.px)
                }

                props.attributeEditorManager.child(this) {
                    this.objectLocation = props.common.objectLocation
                    this.attributeName = ScriptConventions.conditionAttributeName
                }
            }
        }
    }
}
