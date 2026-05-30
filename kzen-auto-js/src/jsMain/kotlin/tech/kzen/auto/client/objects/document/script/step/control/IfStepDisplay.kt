package tech.kzen.auto.client.objects.document.script.step.control

import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayProps
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayWrapper
import tech.kzen.auto.client.objects.document.script.display.StepDisplayManager
import tech.kzen.auto.client.objects.document.script.display.branch.branchHeaderSlab
import tech.kzen.auto.client.objects.document.script.display.branch.branchStageLedge
import tech.kzen.auto.client.objects.document.script.display.branch.branchStageSeam
import tech.kzen.auto.client.objects.document.script.display.branch.branchStageTopShadow
import tech.kzen.auto.client.objects.document.script.display.branch.scriptBranchContainer
import tech.kzen.auto.client.objects.document.script.display.computeStepHeaderInfo
import tech.kzen.auto.client.objects.document.script.display.computeStepTraceInfo
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreContext
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import web.cssom.Color
import web.cssom.NamedColor
import web.cssom.Position
import web.cssom.deg
import web.cssom.linearGradient
import web.cssom.px
import web.cssom.stop


//---------------------------------------------------------------------------------------------------------------------
external interface IfStepDisplayProps: ScriptStepDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var stepDisplayManager: StepDisplayManager.Wrapper
    var scriptCommander: ScriptCommander
}


external interface IfStepDisplayState: State {
    var stepTrace: StepTrace?
    var isNextToRun: Boolean?

    var icon: String?
    var description: String?
    var title: String?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class IfStepDisplay(
    props: IfStepDisplayProps
):
    RPureComponent<IfStepDisplayProps, IfStepDisplayState>(props),
    ClientStateGlobal.Observer,
    ScriptStore.Observer
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
        private val scriptCommander: ScriptCommander
    ):
        ScriptStepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: ScriptStepDisplayProps.() -> Unit) {
            IfStepDisplay::class.react {
                attributeEditorManager = this@Wrapper.attributeEditorManager
                stepDisplayManager = this@Wrapper.stepDisplayManager.wrapper!!
                scriptCommander = this@Wrapper.scriptCommander

                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(ScriptStoreContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.clientStateGlobal.observe(this)
        contextValue<ScriptStore?>()?.observe(this)
    }


    override fun componentWillUnmount() {
        contextValue<ScriptStore?>()?.unobserve(this)
        ClientContext.clientStateGlobal.unobserve(this)
    }


    override fun onClientState(clientState: ClientState) {
        val info = computeStepHeaderInfo(clientState, props.common.objectLocation)
            ?: return

        // NB: skip setState on no-op publishes so a sibling step's change doesn't re-render this body
        //     (and so the RPureComponent conversion isn't defeated by an unconditional setState).
        if (state.icon == info.icon &&
            state.description == info.description &&
            state.title == info.title
        ) {
            return
        }

        setState {
            this.icon = info.icon
            this.description = info.description
            this.title = info.title
        }
    }


    override fun onScriptState(scriptState: ScriptState) {
        val info = computeStepTraceInfo(
            scriptState, props.common.objectLocation, ClientContext.objectStableMapper)

        // NB: value compare (==) — computeStepTraceInfo rebuilds a fresh StepTrace each call (value-equal,
        //     not ===). Skip setState when THIS step is unchanged so a sibling's expand/collapse or trace
        //     update doesn't re-render every step body.
        if (state.stepTrace == info.trace &&
            state.isNextToRun == info.isNextToRun
        ) {
            return
        }

        setState {
            this.stepTrace = info.trace
            this.isNextToRun = info.isNextToRun
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        branchHeaderSlab(
            indexInParent = props.common.indexInParent,
            objectLocation = props.common.objectLocation,
            icon = state.icon ?: "",
            description = state.description ?: "",
            title = state.title ?: "",
            trace = state.stepTrace,
            isNextToRun = state.isNextToRun ?: false
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

            // Then: shared seam + top down-shadow (cast from the white condition above), wrapped in
            // an If-specific fade up to white at the bottom. That bottom fade forms the white "lip"
            // sitting just above the Else seam — standing in for a white slab there, the way the
            // white condition sits above the Then seam — so both seams read the same (white above →
            // 1px line → shadow below). Paint-only, masked by the white trunk. Outer div = bottom
            // fade, inner branchStageTopShadow = top shadow (two anchors → two divs).
            branchStageSeam()
            div {
                css {
                    backgroundImage = linearGradient(
                        0.deg,
                        stop(NamedColor.white, 0.px),                 // white at the bottom (the Else seam)
                        stop(Color("rgba(255, 255, 255, 0)"), 14.px)) // sharp fade up to transparent
                }
                branchStageTopShadow {
                    renderThenBranch()
                }
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
            scriptCommander = props.scriptCommander)
    }


    private fun ChildrenBuilder.renderElseBranch() {
        scriptBranchContainer(
            label = "Else",
            branchLocation = AttributeLocation(props.common.objectLocation, elseAttributePath),
            stepDisplayManager = props.stepDisplayManager,
            scriptCommander = props.scriptCommander)
    }
}
