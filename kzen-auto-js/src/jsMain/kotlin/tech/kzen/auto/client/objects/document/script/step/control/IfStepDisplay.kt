package tech.kzen.auto.client.objects.document.script.step.control

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.display.*
import tech.kzen.auto.client.objects.document.script.display.branch.*
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.*
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.Position


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class IfStepDisplay(
    props: BranchStepDisplayProps
):
    ScriptStepDisplayBase<BranchStepDisplayProps, ScriptStepDisplayBaseState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val conditionAttributeName = AttributeName("condition")

        val thenAttributeName = AttributeName("then")
        private val thenAttributePath = AttributePath.ofName(thenAttributeName)

        val elseAttributeName = AttributeName("else")
        private val elseAttributePath = AttributePath.ofName(elseAttributeName)
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
        branchHeaderSlab(
            objectLocation = props.common.objectLocation,
            icon = state.icon ?: "",
            description = state.description ?: "",
            title = state.title ?: "",
            trace = state.stepTrace,
            isNextToRun = state.isNextToRun ?: false,
            mirroredGraphStore = props.mirroredGraphStore,
            typeMetadata = state.stepValidation?.typeMetadata?.toSimple(),
            validationError = state.stepValidation?.errorMessage
        ) {
            props.attributeEditorManager.child(this) {
                this.objectLocation = props.common.objectLocation
                this.attributeName = conditionAttributeName
            }
        }

        // Recessed-stage chrome wrapper, mirroring the page-level header/sidebar casting a shadow
        // onto the gray stage. The white condition slab above plays the "header" role, the white
        // trunk the "sidebar". Decorations are the shared branchStage* helpers; the only If-specific
        // piece is the Then branch's bottom fade-to-white lip (see below).
        div {
            css {
                position = Position.relative
            }

            // Vertical ledge down the trunk's right edge, continuous through both branches.
            branchStageLedge()

            // Outer frame: hairline + soft shade down the trunk's left edge and across its bottom,
            // with rounded bottom corners — completing the white "⌐" card frame (header = top).
            branchStageBase()

            // Then: shared seam + top down-shadow (cast from the white condition above). The white
            // "lip" at the bottom of this branch (branchStageThenLip) stands in for a white slab
            // above the Else seam, so both seams read the same (white above → 1px line → shadow
            // below, the way the white condition slab sits above the Then seam).
            branchStageSeam()
            div {
                css {
                    // position:relative so the lip's bottom:0 anchors to THIS branch's bottom (not
                    // the whole construct's), and so the lip sits in the positioned paint layer above
                    // the absolutely-positioned branchStageLedge — the lip interrupts the ledge where
                    // they cross, keeping the Else seam's white line crisp and continuous.
                    position = Position.relative
                }
                branchStageTopShadow {
                    renderThenBranch()
                }
                branchStageThenLip()
            }

            // Else: shared seam + top down-shadow, cast from the white lip formed above.
            branchStageSeam()
            branchStageTopShadow {
                renderElseBranch()
            }
        }
    }


    private fun ChildrenBuilder.renderThenBranch() {
        scriptBranchContainer(
            label = "Then",
            branchLocation = AttributeLocation(props.common.objectLocation, thenAttributePath),
            stepDisplayManager = props.stepDisplayManager,
            scriptCommander = props.scriptCommander,
            roundedBottom = false,
            clientStateGlobal = props.clientStateGlobal,
            mirroredGraphStore = props.mirroredGraphStore,
            objectStableMapper = props.objectStableMapper)
    }


    private fun ChildrenBuilder.renderElseBranch() {
        scriptBranchContainer(
            label = "Else",
            branchLocation = AttributeLocation(props.common.objectLocation, elseAttributePath),
            stepDisplayManager = props.stepDisplayManager,
            scriptCommander = props.scriptCommander,
            roundedBottom = true,
            clientStateGlobal = props.clientStateGlobal,
            mirroredGraphStore = props.mirroredGraphStore,
            objectStableMapper = props.objectStableMapper)
    }
}
