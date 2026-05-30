package tech.kzen.auto.client.objects.document.script.step.control.mapping

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
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import web.cssom.Position


//---------------------------------------------------------------------------------------------------------------------
external interface MappingStepDisplayProps: ScriptStepDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var stepDisplayManager: StepDisplayManager.Wrapper
    var scriptCommander: ScriptCommander
}


external interface MappingStepDisplayState: State {
    var stepTrace: StepTrace?
    var isNextToRun: Boolean?

    var icon: String?
    var description: String?
    var title: String?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class MappingStepDisplay(
    props: MappingStepDisplayProps
):
    RPureComponent<MappingStepDisplayProps, MappingStepDisplayState>(props),
    ClientStateGlobal.Observer,
    ScriptStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val itemsAttributeName = AttributeName("items")
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
            MappingStepDisplay::class.react {
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
                this.attributeName = itemsAttributeName
            }
        }

        // Recessed-stage chrome wrapper, mirroring the page-level header/sidebar casting a shadow
        // onto the gray stage (same treatment as IfStepDisplay's branches). The white items slab
        // above plays the "header" role, the white trunk the "sidebar". A single branch ("Each"),
        // so just the shared seam + top down-shadow, plus the vertical ledge down the trunk's edge.
        div {
            css {
                position = Position.relative
            }

            branchStageLedge()
            branchStageSeam()
            branchStageTopShadow {
                renderSteps()
            }
        }
    }


    private fun ChildrenBuilder.renderSteps() {
        scriptBranchContainer(
            label = "Each",
            branchLocation = AttributeLocation(props.common.objectLocation, ScriptConventions.stepsAttributePath),
            stepDisplayManager = props.stepDisplayManager,
            scriptCommander = props.scriptCommander)
    }
}
