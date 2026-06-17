package tech.kzen.auto.client.objects.document.script.step.control

import emotion.react.css
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayDefault
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayProps
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayWrapper
import tech.kzen.auto.client.objects.document.script.display.image.ScreenshotThumbnail
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreContext
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.model.RunStepInstructions
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceEvent
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper
import web.cssom.AlignItems
import web.cssom.Color
import web.cssom.Cursor
import web.cssom.Display
import web.cssom.FlexWrap
import web.cssom.FontWeight
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
    // The detail film strip: screenshot events captured anywhere in this RunStep's subtree, grouped by
    // sub-script execution (in execution order). Derived from ScriptProgressState.traceEvents.
    var groups: List<ScreenshotGroup>

    // Cheap change signature (subtree roots + frame count + last sequence) so no-op progress ticks
    // skip setState; structural byte-array comparison of frames would be needlessly expensive.
    var signature: String

    // Execution ids whose group the user has collapsed.
    var collapsedExecutions: Set<String>
}


// One execution's screenshots in the strip: a sub-script invocation's frames, in execution order.
data class ScreenshotGroup(
    val executionId: String,
    val label: String,
    val frames: List<LogicTraceEvent>
)


//---------------------------------------------------------------------------------------------------------------------
// Display for a RunStep: the unchanged default step card, with a screenshot film strip rendered into
// the card's expanded body via expandedBodyExtra. The strip shows every screenshot captured anywhere
// under this RunStep — all nested sub-scripts and all loop iterations — in execution order, grouped
// and labelled by sub-script execution (groups collapsible). Frames come from the retained trace
// timeline (ScriptProgressStore.traceEvents); the collapsed RunStep's representative (the generic
// thumbnail to the right, rendered by ScriptBranchDisplay) shows the subtree's latest frame.
//
// Observes ScriptState only: the timeline lives there, and graph changes also republish it, so the
// subtree (from the current graph, read via clientStateGlobal) and the frames stay in sync.
@Suppress("unused")
class RunStepDisplay(
    props: RunStepDisplayProps
):
    RPureComponent<RunStepDisplayProps, RunStepDisplayState>(props),
    ScriptStore.Observer
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
        installContextType(ScriptStoreContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        // observe replays onScriptState synchronously with the current state.
        contextValue<ScriptStore?>()?.observe(this)
    }


    override fun componentWillUnmount() {
        contextValue<ScriptStore?>()?.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RunStepDisplayState.init(props: RunStepDisplayProps) {
        groups = listOf()
        signature = ""
        collapsedExecutions = setOf()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onScriptState(scriptState: ScriptState) {
        val clientState = props.clientStateGlobal.current()
            ?: return
        val graphNotation = clientState.graphStructure().graphNotation
        if (props.common.objectLocation !in graphNotation.coalesce) {
            // NB: this step was deleted or renamed and this objectLocation is stale.
            return
        }

        val subtreeRoots = RunStepInstructions
            .subtreeInstructionRoots(graphNotation, props.common.objectLocation)
            .mapTo(mutableSetOf()) { props.objectStableMapper.objectStableId(it) }

        // traceEvents is sorted by sequence; keep that order so groups read in execution order.
        val relevant = scriptState.progress.traceEvents
            .filter { it.value is BinaryExecutionValue && it.rootStableId in subtreeRoots }

        val signature = subtreeRoots.joinToString(",") { it.value } + "|" +
                relevant.size + "|" + (relevant.lastOrNull()?.sequence ?: -1L)
        if (signature == state.signature) {
            return
        }

        val groups = buildGroups(relevant)

        setState {
            this.groups = groups
            this.signature = signature
        }
    }


    // Group consecutive frames by execution (one sub-script invocation = one buffer = one group),
    // preserving execution order. Loop iterations whose body is a RunStep are separate invocations, so
    // a repeated sub-script root gets an ordinal (e.g. "Build Item #2") to distinguish them.
    private fun buildGroups(frames: List<LogicTraceEvent>): List<ScreenshotGroup> {
        val byExecution = LinkedHashMap<String, MutableList<LogicTraceEvent>>()
        for (frame in frames) {
            byExecution.getOrPut(frame.executionId.value) { mutableListOf() }.add(frame)
        }

        val groupFramesList = byExecution.values.toList()
        val rootTotals = groupFramesList
            .groupingBy { it.first().rootStableId }
            .eachCount()
        val rootSeen = mutableMapOf<ObjectStableId, Int>()

        return groupFramesList.map { groupFrames ->
            val root = groupFrames.first().rootStableId
            val seen = (rootSeen[root] ?: 0) + 1
            rootSeen[root] = seen
            val name = rootLabel(root)
            val label = if ((rootTotals[root] ?: 1) > 1) "$name #$seen" else name
            ScreenshotGroup(groupFrames.first().executionId.value, label, groupFrames)
        }
    }


    private fun rootLabel(root: ObjectStableId): String {
        return try {
            props.objectStableMapper.objectLocation(root).documentPath.name.value
        }
        catch (_: IllegalArgumentException) {
            "?"
        }
    }


    private fun toggleCollapsed(executionId: String) {
        // NB: read prior value OUTSIDE the setState lambda — it runs on an empty object (write-only).
        val current = state.collapsedExecutions
        val next =
            if (executionId in current) {
                current - executionId
            }
            else {
                current + executionId
            }
        setState {
            collapsedExecutions = next
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        // The standard step card, unchanged (header, arguments editor, expand/collapse, trace).
        // The screenshot film strip is rendered into its expanded body via expandedBodyExtra.
        ScriptStepDisplayDefault::class.react {
            this.attributeEditorManager = props.attributeEditorManager
            this.attributeViewManager = props.attributeViewManager
            this.clientStateGlobal = props.clientStateGlobal
            this.objectStableMapper = props.objectStableMapper
            this.mirroredGraphStore = props.mirroredGraphStore
            this.common = props.common

            if (state.groups.isNotEmpty()) {
                this.expandedBodyExtra = { bodyBuilder -> bodyBuilder.renderScreenshotGroups() }
            }
        }
    }


    private fun ChildrenBuilder.renderScreenshotGroups() {
        div {
            css {
                marginTop = 0.5.em
            }

            for (group in state.groups) {
                renderGroup(group)
            }
        }
    }


    private fun ChildrenBuilder.renderGroup(group: ScreenshotGroup) {
        val collapsed = group.executionId in state.collapsedExecutions

        div {
            key = Key(group.executionId)
            css {
                marginBottom = 0.5.em
            }

            div {
                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                    cursor = Cursor.pointer
                    fontSize = 0.85.em
                    color = Color("rgba(0, 0, 0, 0.6)")
                }
                onClick = { toggleCollapsed(group.executionId) }

                span { +(if (collapsed) "▶ " else "▼ ") }
                span {
                    css {
                        fontWeight = FontWeight.bold
                    }
                    +group.label
                }
                span {
                    css {
                        marginLeft = 0.5.em
                    }
                    +"(${group.frames.size})"
                }
            }

            if (!collapsed) {
                div {
                    css {
                        display = Display.flex
                        flexWrap = FlexWrap.wrap
                        alignItems = AlignItems.flexStart
                    }

                    for (frame in group.frames) {
                        ScreenshotThumbnail::class.react {
                            key = Key(frame.sequence.toString())
                            screenshot = frame.value as BinaryExecutionValue
                            label = group.label

                            // Hovering a frame drives this RunStep's right-of-step big preview; null on
                            // leave reverts it to the latest representative frame.
                            onPreviewHover = { hovered ->
                                contextValue<ScriptStore?>()
                                    ?.stepStore
                                    ?.setHoveredScreenshot(props.common.objectLocation, hovered)
                            }
                        }
                    }
                }
            }
        }
    }
}
