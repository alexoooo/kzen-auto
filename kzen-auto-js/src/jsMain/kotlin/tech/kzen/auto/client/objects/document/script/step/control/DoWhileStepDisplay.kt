package tech.kzen.auto.client.objects.document.script.step.control

import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.display.*
import tech.kzen.auto.client.objects.document.script.display.branch.branchStageLedge
import tech.kzen.auto.client.objects.document.script.display.branch.branchStageSeam
import tech.kzen.auto.client.objects.document.script.display.branch.branchStageTopShadow
import tech.kzen.auto.client.objects.document.script.display.branch.scriptBranchContainer
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreKey
import tech.kzen.auto.client.objects.document.script.step.header.StepHeader
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.StepTrace
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface DoWhileStepDisplayProps: ScriptStepDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var stepDisplayManager: StepDisplayManager.Wrapper
    var scriptCommander: ScriptCommander

    var clientStateGlobal: ClientStateGlobal
    var objectStableMapper: ObjectStableMapper
    var mirroredGraphStore: MirroredGraphStore
}


external interface DoWhileStepDisplayState: State {
    var stepTrace: StepTrace?
    var isNextToRun: Boolean?

    var icon: String?
    var description: String?
    var title: String?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class DoWhileStepDisplay(
    props: DoWhileStepDisplayProps
):
    RPureComponent<DoWhileStepDisplayProps, DoWhileStepDisplayState>(props),
    ClientStateGlobal.Observer,
    ScriptStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val conditionAttributeName = AttributeName("condition")

        // Hairline matching the branch-stage seam, used to separate the white While footer from the
        // gray Do stage above it.
        private val bodySeamColor = Color("rgba(0, 0, 0, 0.12)")

        // Status accent line down the left edge of the whole body — same 4px coloured border the leaf
        // step card uses (white when idle, so there's no layout shift when a run starts).
        private val accentWidth = 4.px
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
        // Run status reads as a 4px coloured line down the LEFT of the whole body (the leaf step card's
        // pattern), not a header tint — so the header stays neutral and the accent spans header → Do →
        // While as one continuous edge.
        val trace = state.stepTrace
        val traceState = trace?.state ?: StepTrace.State.Idle
        val accent = ScriptStepDisplayDefault.statusBorderColor(
            traceState, trace?.error, state.isNextToRun ?: false)

        // Three flush sections, ordered to match the do-while (run the body, THEN test the condition):
        //   header (neutral white) → "Do" body steps (recessed gray stage) → full-width "While" footer.
        renderHeader(accent)
        renderDoStage(accent)
        renderWhileFooter(accent)
    }


    // Neutral white title bar (rounded top); the status colour is carried by the left accent only.
    private fun ChildrenBuilder.renderHeader(accent: Color) {
        div {
            css {
                backgroundColor = NamedColor.white
                borderTopLeftRadius = ScriptStepDisplayDefault.cardCornerRadius
                borderTopRightRadius = ScriptStepDisplayDefault.cardCornerRadius
                borderLeftWidth = accentWidth
                borderLeftStyle = LineStyle.solid
                borderLeftColor = accent
                boxShadow = ScriptStepDisplayDefault.cardRestingShadow
                paddingBottom = 0.5.em
            }

            div {
                css {
                    padding = Padding(16.px, 16.px, 0.px, 16.px)
                }

                StepHeader::class.react {
                    this.objectLocation = props.common.objectLocation
                    managed = false
                    this.icon = state.icon ?: ""
                    this.description = state.description ?: ""
                    this.title = state.title ?: ""
                    this.mirroredGraphStore = props.mirroredGraphStore
                }
            }
        }
    }


    // The "Do" body: the step list on the recessed gray stage. White "Do" trunk on the left (the
    // accent border is the trunk's left edge), the gray page stage on the right where step cards float.
    // No rounded bottom — the While footer below completes the frame.
    private fun ChildrenBuilder.renderDoStage(accent: Color) {
        div {
            css {
                position = Position.relative
                borderLeftWidth = accentWidth
                borderLeftStyle = LineStyle.solid
                borderLeftColor = accent
            }

            // Vertical ledge down the trunk's right edge; seam + down-shadow under the header above.
            branchStageLedge()
            branchStageSeam()
            branchStageTopShadow {
                scriptBranchContainer(
                    label = "Do",
                    branchLocation = AttributeLocation(
                        props.common.objectLocation, ScriptConventions.stepsAttributePath),
                    stepDisplayManager = props.stepDisplayManager,
                    scriptCommander = props.scriptCommander,
                    roundedBottom = false,
                    clientStateGlobal = props.clientStateGlobal,
                    mirroredGraphStore = props.mirroredGraphStore,
                    objectStableMapper = props.objectStableMapper)
            }
        }
    }


    // The "While" condition: a full-width white footer (NOT a branch — no step list, no gray stage).
    // The "While" label sits in a left column aligned under the "Do" trunk; the Kotlin Boolean editor
    // extends horizontally across the rest. Rounded bottom completes the construct's frame.
    private fun ChildrenBuilder.renderWhileFooter(accent: Color) {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.stretch
                backgroundColor = NamedColor.white
                borderLeftWidth = accentWidth
                borderLeftStyle = LineStyle.solid
                borderLeftColor = accent
                borderTop = Border(1.px, LineStyle.solid, bodySeamColor)
                borderBottomLeftRadius = ScriptStepDisplayDefault.cardCornerRadius
                borderBottomRightRadius = ScriptStepDisplayDefault.cardCornerRadius
                boxShadow = ScriptStepDisplayDefault.cardRestingShadow
            }

            // "While" label column — same width as the Do trunk so the two labels align vertically.
            div {
                css {
                    width = 3.em
                    flexShrink = number(0.0)
                    padding = Padding(16.px, 0.75.em)
                    color = Color("rgba(0, 0, 0, 0.7)")
                    display = Display.flex
                    alignItems = AlignItems.center
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
                    this.attributeName = conditionAttributeName
                }
            }
        }
    }
}
