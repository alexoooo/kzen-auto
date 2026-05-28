package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.RefObject
import react.State
import react.dom.events.DragEvent
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.dragdrop.DropMarker
import tech.kzen.auto.client.objects.document.common.dragdrop.dragHandle
import tech.kzen.auto.client.objects.document.common.dragdrop.dropIndicator
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.*
import web.html.HTMLDivElement


//---------------------------------------------------------------------------------------------------------------------
external interface ScriptStepSlotProps: Props {
    var objectLocation: ObjectLocation
    var indexInParent: Int
    var first: Boolean
    var last: Boolean

    var dropMarker: DropMarker?
    var isDragSource: Boolean

    var stepDisplayManager: StepDisplayManager.Wrapper
    var handleColor: Color

    var onDragStart: () -> Unit
    var onDragOver: (DragEvent<HTMLDivElement>) -> Unit
    var onDrop: (DragEvent<HTMLDivElement>) -> Unit
    var onDragEnd: () -> Unit
}


external interface ScriptStepSlotState: State {
    var isHovered: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class ScriptStepSlot(
    props: ScriptStepSlotProps
):
    RPureComponent<ScriptStepSlotProps, ScriptStepSlotState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // NB: global registry of "drag-hover yield zones" — DOM elements whose presence between the
        //     event target and an enclosing slot's root means the enclosing slot's drag handle
        //     should NOT light up. Two kinds register here:
        //       - Nested ScriptStepSlot roots (so an inner step's handle takes priority over the
        //         outer's; e.g. Text inside an If's Then branch).
        //       - ScriptBranchDisplay roots (so the gap/padding inside a branch belongs to no step;
        //         e.g. the gray area between two steps inside If's Then doesn't activate If's
        //         handle).
        //     A JS-level Set keyed on element references avoids the className/data-attribute
        //     spelling and emotion-css conflicts that an earlier `.closest()` approach hit.
        private val yieldZoneRoots = mutableSetOf<HTMLDivElement>()

        internal fun registerYieldZone(element: HTMLDivElement) {
            yieldZoneRoots.add(element)
        }

        internal fun unregisterYieldZone(element: HTMLDivElement) {
            yieldZoneRoots.remove(element)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // NB: kept stable across hover toggles so StepDisplayManager (RPureComponent) can bail out
    private var cachedCommon: ScriptStepDisplayPropsCommon? = null

    private val rootRef: RefObject<HTMLDivElement> = createRef()


    //-----------------------------------------------------------------------------------------------------------------
    override fun ScriptStepSlotState.init(props: ScriptStepSlotProps) {
        isHovered = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        rootRef.current?.let { registerYieldZone(it) }
    }


    override fun componentWillUnmount() {
        rootRef.current?.let { unregisterYieldZone(it) }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun commonForProps(): ScriptStepDisplayPropsCommon {
        val existing = cachedCommon
        if (existing != null &&
            existing.objectLocation === props.objectLocation &&
            existing.indexInParent == props.indexInParent &&
            existing.first == props.first &&
            existing.last == props.last
        ) {
            return existing
        }
        val fresh = ScriptStepDisplayPropsCommon(
            props.objectLocation,
            props.indexInParent,
            first = props.first,
            last = props.last)
        cachedCommon = fresh
        return fresh
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun hasYieldZoneInPath(event: react.dom.events.MouseEvent<HTMLDivElement, *>): Boolean {
        val current = rootRef.current
            ?: return false
        var node: dynamic = event.target
        while (node != null && node !== current) {
            @Suppress("UNCHECKED_CAST")
            val asDiv = node.unsafeCast<HTMLDivElement>()
            if (yieldZoneRoots.contains(asDiv)) {
                return true
            }
            node = node.parentElement
        }
        return false
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            ref = rootRef
            css {
                position = Position.relative
                height = 100.pct
            }

            // NB: mouseEnter/mouseLeave handle the basic show/hide (they fire on element-level
            //     entry/exit and don't bubble through descendants). mouseOver additionally fires
            //     on every descendant transition and is used purely to SUPPRESS the outer slot's
            //     hover when the cursor moves into a nested slot (If's Then/Else case).
            onMouseEnter = {
                if (!state.isHovered) {
                    setState { isHovered = true }
                }
            }
            onMouseLeave = {
                if (state.isHovered) {
                    setState { isHovered = false }
                }
            }
            onMouseOver = { event ->
                val effectivelyHovered = !hasYieldZoneInPath(event)
                if (state.isHovered != effectivelyHovered) {
                    setState { isHovered = effectivelyHovered }
                }
            }
            onDragOver = props.onDragOver
            onDrop = props.onDrop

            dragHandle(
                isVisible = state.isHovered || props.isDragSource,
                handleColor = props.handleColor,
                onStart = props.onDragStart,
                onEnd = props.onDragEnd,
                frosted = true)
            dropIndicator(props.dropMarker)

            props.stepDisplayManager.child(this) {
                common = commonForProps()
            }
        }
    }
}
