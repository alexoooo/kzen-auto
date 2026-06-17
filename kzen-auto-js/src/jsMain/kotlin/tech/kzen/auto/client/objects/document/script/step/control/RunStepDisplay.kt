package tech.kzen.auto.client.objects.document.script.step.control

import emotion.react.css
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayDefault
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayProps
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayWrapper
import tech.kzen.auto.client.objects.document.script.display.image.StepImageThumbnail
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.model.RunStepInstructions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.AlignItems
import web.cssom.Display
import web.cssom.FlexWrap
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface RunStepDisplayProps: ScriptStepDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper
    var clientStateGlobal: ClientStateGlobal
    var objectStableMapper: ObjectStableMapper
    var mirroredGraphStore: MirroredGraphStore
}


external interface RunStepDisplayState: State {
    var subStepLocations: List<ObjectLocation>
}


//---------------------------------------------------------------------------------------------------------------------
// Display for a RunStep: the unchanged default step card, with a strip of screenshot thumbnails —
// one per step of the linked instructions sub-script — rendered into the card's expanded body via
// the default display's expandedBodyExtra slot. Each thumbnail resolves its screenshot from the
// merged trace snapshot (sub-script traces folded in by ScriptProgressStore) and reuses
// StepImageThumbnail's hover preview + click-to-fullscreen. The collapsed RunStep's representative
// thumbnail (the generic one to the right, rendered by ScriptBranchDisplay) shows the last sub-script
// screenshot via the representativeScreenshotLocation resolver — not handled here.
//
// The sub-script step set is structural (from the graph), so it's tracked via ClientStateGlobal;
// expansion gating is left to the default card (it only renders the slot when expanded), so this
// component needs no expansion state of its own.
@Suppress("unused")
class RunStepDisplay(
    props: RunStepDisplayProps
):
    RPureComponent<RunStepDisplayProps, RunStepDisplayState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        private val attributeViewManager: AttributeViewManager.Wrapper,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val objectStableMapper: ObjectStableMapper,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        ScriptStepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: ScriptStepDisplayProps.() -> Unit) {
            RunStepDisplay::class.react {
                this.attributeEditorManager = this@Wrapper.attributeEditorManager
                this.attributeViewManager = this@Wrapper.attributeViewManager
                this.clientStateGlobal = this@Wrapper.clientStateGlobal
                this.objectStableMapper = this@Wrapper.objectStableMapper
                this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        // Reach the ScriptStore (its stepStore) so strip-thumbnail hovers can set this RunStep's
        // preview override — the same context the sibling StepImageThumbnail reads.
        installContextType(ScriptStoreContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RunStepDisplayState.init(props: RunStepDisplayProps) {
        subStepLocations = listOf()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The sub-script step set is structural (from the graph), so it's recomputed on client-state
    // publishes — including edits to the instructions link or to the sub-script document itself.
    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphStructure().graphNotation
        if (props.common.objectLocation !in graphNotation.coalesce) {
            // NB: this step was deleted or renamed and this objectLocation is stale.
            return
        }

        val graphDefinition = clientState.graphDefinitionAttempt.successful()
        val instructionsLocation = RunStepInstructions.instructionsLocation(
            graphNotation, props.common.objectLocation)

        val subStepLocations =
            if (instructionsLocation != null) {
                RunStepInstructions.subScriptStepLocations(graphDefinition, instructionsLocation)
            }
            else {
                listOf()
            }

        // NB: value compare (structural List ==) — skip setState on no-op publishes so a sibling
        //     step's change doesn't re-render this card.
        if (state.subStepLocations == subStepLocations) {
            return
        }

        setState {
            this.subStepLocations = subStepLocations
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        // The standard step card, unchanged (handles header, arguments editor, expand/collapse, trace).
        // The sub-script thumbnail strip is rendered into its expanded body via expandedBodyExtra.
        ScriptStepDisplayDefault::class.react {
            this.attributeEditorManager = props.attributeEditorManager
            this.attributeViewManager = props.attributeViewManager
            this.clientStateGlobal = props.clientStateGlobal
            this.objectStableMapper = props.objectStableMapper
            this.mirroredGraphStore = props.mirroredGraphStore
            this.common = props.common

            if (state.subStepLocations.isNotEmpty()) {
                this.expandedBodyExtra = { bodyBuilder -> bodyBuilder.renderSubScriptThumbnails() }
            }
        }
    }


    private fun ChildrenBuilder.renderSubScriptThumbnails() {
        div {
            css {
                display = Display.flex
                flexWrap = FlexWrap.wrap
                alignItems = AlignItems.flexStart
                marginTop = 0.5.em
            }

            for (subStepLocation in state.subStepLocations) {
                // Steps without a screenshot render nothing (StepImageThumbnail early-returns on
                // null), so the strip self-populates as sub-script steps produce frames.
                StepImageThumbnail::class.react {
                    key = Key(subStepLocation.asString())
                    objectLocation = subStepLocation
                    objectStableMapper = props.objectStableMapper
                    clientStateGlobal = props.clientStateGlobal

                    // Delegate the preview: hovering a strip thumbnail sets this RunStep's preview
                    // override (the frame the right-of-step thumbnail shows); null on leave reverts
                    // it to the latest frame.
                    onPreviewHover = { hoveredLocation ->
                        contextValue<ScriptStore?>()
                            ?.stepStore
                            ?.setHoveredScreenshot(props.common.objectLocation, hoveredLocation)
                    }
                }
            }
        }
    }
}
