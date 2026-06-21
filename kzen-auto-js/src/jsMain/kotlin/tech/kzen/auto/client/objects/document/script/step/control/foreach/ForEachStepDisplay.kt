package tech.kzen.auto.client.objects.document.script.step.control.foreach

import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.display.*
import tech.kzen.auto.client.objects.document.script.display.branch.*
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreKey
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ForEachStepDisplayProps: ScriptStepDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var stepDisplayManager: StepDisplayManager.Wrapper
    var scriptCommander: ScriptCommander

    var clientStateGlobal: ClientStateGlobal
    var objectStableMapper: ObjectStableMapper
    var mirroredGraphStore: MirroredGraphStore
}


external interface ForEachStepDisplayState: State {
    var stepTrace: StepTrace?
    var isNextToRun: Boolean?

    var icon: String?
    var description: String?
    var title: String?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class ForEachStepDisplay(
    props: ForEachStepDisplayProps
):
    RPureComponent<ForEachStepDisplayProps, ForEachStepDisplayState>(props),
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
        private val scriptCommander: ScriptCommander,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val objectStableMapper: ObjectStableMapper,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        ScriptStepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: ScriptStepDisplayProps.() -> Unit) {
            ForEachStepDisplay::class.react {
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
    init {
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
        contextValue<DocumentBridge?>()?.lookup(ScriptStoreKey)?.observe(this)
    }


    override fun componentWillUnmount() {
        contextValue<DocumentBridge?>()?.lookup(ScriptStoreKey)?.unobserve(this)
        props.clientStateGlobal.unobserve(this)
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
            scriptState, props.common.objectLocation, props.objectStableMapper)

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
            objectLocation = props.common.objectLocation,
            icon = state.icon ?: "",
            description = state.description ?: "",
            title = state.title ?: "",
            trace = state.stepTrace,
            isNextToRun = state.isNextToRun ?: false,
            mirroredGraphStore = props.mirroredGraphStore
        ) {
            props.attributeEditorManager.child(this) {
                this.objectLocation = props.common.objectLocation
                this.attributeName = itemsAttributeName
            }

            renderCurrentItem()
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

            // Outer frame: hairline + soft shade down the trunk's left edge and across its bottom,
            // with rounded bottom corners — completing the white "⌐" card frame (header = top).
            branchStageBase()

            branchStageSeam()
            branchStageTopShadow {
                renderSteps()
            }
        }
    }


    // The current iteration's item value, surfaced live as the ForEach runs (traced as the step's
    // detail by ForEachStep). Hidden until there's an item to show.
    private fun ChildrenBuilder.renderCurrentItem() {
        val detail = state.stepTrace?.detail
        if (detail == null || detail is NullExecutionValue) {
            return
        }

        div {
            css {
                marginTop = 0.25.em
                marginBottom = 0.5.em
                fontSize = 0.85.em
                color = Color("gray")
            }

            +"item: "
            span {
                css {
                    fontWeight = FontWeight.bold
                    color = NamedColor.black
                }
                +executionValueText(detail)
            }
        }
    }


    private fun executionValueText(value: ExecutionValue): String {
        return when (value) {
            is ScalarExecutionValue -> value.get().toString()
            is ListExecutionValue -> value.values.map { it.get() }.toString()
            else -> value.toString()
        }
    }


    private fun ChildrenBuilder.renderSteps() {
        scriptBranchContainer(
            label = "Each",
            branchLocation = AttributeLocation(props.common.objectLocation, ScriptConventions.stepsAttributePath),
            stepDisplayManager = props.stepDisplayManager,
            scriptCommander = props.scriptCommander,
            roundedBottom = true,
            clientStateGlobal = props.clientStateGlobal,
            mirroredGraphStore = props.mirroredGraphStore,
            objectStableMapper = props.objectStableMapper)
    }
}
