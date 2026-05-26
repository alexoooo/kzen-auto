package tech.kzen.auto.client.objects.document.script.display

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.events.DragEvent
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.dragdrop.DropMarker
import tech.kzen.auto.client.objects.document.common.dragdrop.dragHandle
import tech.kzen.auto.client.objects.document.common.dragdrop.dropIndicator
import tech.kzen.auto.client.wrap.RPureComponent
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
    // NB: kept stable across hover toggles so StepDisplayManager (RPureComponent) can bail out
    private var cachedCommon: ScriptStepDisplayPropsCommon? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun ScriptStepSlotState.init(props: ScriptStepSlotProps) {
        isHovered = false
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
    private fun onMouseEnter() {
        if (!state.isHovered) {
            setState { isHovered = true }
        }
    }


    private fun onMouseLeave() {
        if (state.isHovered) {
            setState { isHovered = false }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                position = Position.relative
            }

            onMouseEnter = { onMouseEnter() }
            onMouseLeave = { onMouseLeave() }
            onDragOver = props.onDragOver
            onDrop = props.onDrop

            dragHandle(
                isVisible = state.isHovered || props.isDragSource,
                handleColor = props.handleColor,
                onStart = props.onDragStart,
                onEnd = props.onDragEnd,
                floatOverGutter = true)
            dropIndicator(props.dropMarker)

            props.stepDisplayManager.child(this) {
                common = commonForProps()
            }
        }
    }
}
