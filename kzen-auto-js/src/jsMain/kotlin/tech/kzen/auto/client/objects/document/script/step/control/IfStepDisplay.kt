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
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class IfStepDisplay(
    props: BranchStepDisplayProps
):
    ScriptStepDisplayBase<BranchStepDisplayProps, ScriptStepDisplayBaseState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // IfStep's own branch names, deliberately NOT in ScriptConventions: shared code discovers branches from
        // attribute metadata (`is: List, of: ScriptStep`), so nothing outside this display needs to know them.
        private val thenAttributeName = AttributeName("then")
        private val thenAttributePath = AttributePath.ofName(thenAttributeName)

        private val elseAttributeName = AttributeName("else")
        private val elseAttributePath = AttributePath.ofName(elseAttributeName)

        // Branch label: heading weight over the steps it groups, in the same subdued ink as DoWhile's "While".
        private val branchLabelColor = Color("rgba(0, 0, 0, 0.7)")
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
            IfStepDisplay::class.react {
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
        // The header slab derives this same bar from the trace it is passed; the stage's rail takes it from
        // here, so the construct shows one left edge from the condition down through both branches.
        val trace = state.stepTrace
        val accent = ScriptStepDisplayDefault.statusBorderColor(
            trace?.state ?: StepTrace.State.Idle,
            trace?.error,
            state.isNextToRun ?: false,
            state.stepValidation?.errorMessage)

        branchHeaderSlab(
            objectLocation = props.common.objectLocation,
            icon = state.icon ?: "",
            description = state.description ?: "",
            title = state.title ?: "",
            trace = trace,
            isNextToRun = state.isNextToRun ?: false,
            mirroredGraphStore = props.mirroredGraphStore,
            typeMetadata = state.stepValidation?.typeMetadata?.toSimple(),
            validationError = state.stepValidation?.errorMessage
        ) {
            props.attributeEditorManager.child(this) {
                this.objectLocation = props.common.objectLocation
                this.attributeName = ScriptConventions.conditionAttributeName
            }
        }

        // One recessed stage spanning both branches: a single rail down its whole height, since Then and Else
        // are one construct's scope. Each branch then gets its own seam plus the down-shadow cast onto it from
        // the white surface above — the condition slab for Then, the lip below for Else.
        div {
            css {
                position = Position.relative
            }

            branchStageAccentRail(accent, fadeBottom = true)

            branchStageSeam()
            div {
                css {
                    // position:relative so the lip's bottom:0 anchors to THIS branch's bottom, not the whole
                    // construct's.
                    position = Position.relative
                }
                branchStageTopShadow {
                    renderBranch("Then", thenAttributePath)
                }
                branchStageThenLip()
            }

            branchStageSeam()
            branchStageTopShadow {
                renderBranch("Else", elseAttributePath)
            }
        }
    }


    // Then and Else differ only in label and branch attribute — the indent clearing the rail, the label heading
    // it and the step list under it are one shape.
    private fun ChildrenBuilder.renderBranch(label: String, branchAttributePath: AttributePath) {
        div {
            css {
                // The rail is paint only (absolutely positioned over the stage's left edge), so this indent is
                // the only thing holding the label and the step rows off the scope line.
                marginLeft = branchRailWidth
                minHeight = 4.em
            }

            div {
                css {
                    // Clears the seam above; the step list reserves its own 32px below, so no bottom padding here.
                    paddingTop = 0.75.em
                    fontWeight = FontWeight.bold
                    color = branchLabelColor
                }
                +label
            }

            ScriptBranchDisplay::class.react {
                attributeLocation = AttributeLocation(props.common.objectLocation, branchAttributePath)
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
