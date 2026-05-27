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
import tech.kzen.auto.client.objects.document.script.display.branch.scriptBranchContainer
import tech.kzen.auto.client.objects.document.script.display.computeStepHeaderInfo
import tech.kzen.auto.client.objects.document.script.display.computeStepTraceInfo
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreContext
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RComponent
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
import web.cssom.px


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
    RComponent<IfStepDisplayProps, IfStepDisplayState>(props),
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

        setState {
            this.icon = info.icon
            this.description = info.description
            this.title = info.title
        }
    }


    override fun onScriptState(scriptState: ScriptState) {
        val info = computeStepTraceInfo(scriptState, props.common.objectLocation)

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

        // Full-width 1px gray seam separating the F's top arm (header+condition) from its body.
        // Crosses the trunk on purpose — the user wants the "divider above Then" to go over the
        // left margin too.
        div {
            css {
                height = 1.px
                backgroundColor = Color("rgba(0, 0, 0, 0.12)")
            }
        }

        renderThenBranch()

        // 1px gray seam above the white middle arm, mirroring the seam above Then's branch.
        // Pattern: the seam sits at the TOP of the next white slab (here the arm; for Then it was
        // the header+condition slab) so the slab + seam form a single "section header" unit.
        div {
            css {
                height = 1.px
                backgroundColor = Color("rgba(0, 0, 0, 0.12)")
            }
        }

        // F's middle arm: in-flow 28px white horizontal slab. Width is parent-constrained to the
        // step-body column (ScriptController.stepWidth = 39em — same as the header at the top of
        // the If step), so the arm's right edge aligns with the header above. The 4em trunk
        // strips of Then (above) and Else (below) extend past the arm on the left, keeping the
        // trunk continuous.
        div {
            css {
                height = 28.px
                backgroundColor = NamedColor.white
            }
        }

        renderElseBranch()
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
