package tech.kzen.auto.client.objects.document.custom.view

import react.ChildrenBuilder
import react.Props
import react.State
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.service.notation.NotationConventions


//---------------------------------------------------------------------------------------------------------------------
external interface CustomViewProps: Props {
    var documentPath: DocumentPath
    var clientState: ClientState
    var serverNotation: DocumentObjectNotation
    var customCommander: CustomCommander
    var attributeEditorManager: AttributeEditorManager.Wrapper
}


external interface CustomViewState: State {
    var dragSourceIndex: Int?
    var dragOverIndex: Int?
    var dropAfter: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomView(
    props: CustomViewProps
):
    RPureComponent<CustomViewProps, CustomViewState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private val dragHandlers = CustomDragHandlers(
        onDragStart = ::onCardDragStart,
        onDragOver = ::onCardDragOver,
        onDragEnd = ::onCardDragEnd,
        onDrop = ::onCardDrop)


    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomViewState.init(props: CustomViewProps) {
        dragSourceIndex = null
        dragOverIndex = null
        dropAfter = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onCardDragStart(sourceIndex: Int) {
        setState {
            dragSourceIndex = sourceIndex
            dragOverIndex = null
            dropAfter = false
        }
    }


    private fun onCardDragOver(targetIndex: Int, dropAfter: Boolean) {
        if (state.dragSourceIndex == null) {
            return
        }
        if (state.dragOverIndex == targetIndex && state.dropAfter == dropAfter) {
            return
        }
        setState {
            this.dragOverIndex = targetIndex
            this.dropAfter = dropAfter
        }
    }


    private fun onCardDragEnd() {
        if (state.dragSourceIndex == null && state.dragOverIndex == null) {
            return
        }
        setState {
            dragSourceIndex = null
            dragOverIndex = null
            dropAfter = false
        }
    }


    private fun onCardDrop() {
        props.customCommander.shiftObject(
            state.dragSourceIndex, state.dragOverIndex, state.dropAfter)

        setState {
            dragSourceIndex = null
            dragOverIndex = null
            dropAfter = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val graphStructure = props.clientState.graphStructure()
        val mainObjectLocation = ObjectLocation(props.documentPath, NotationConventions.mainObjectPath)
        val exportsState = CustomExports.current(props.serverNotation, graphStructure, mainObjectLocation)

        for ((index, entry) in props.serverNotation.notations.map.entries.withIndex()) {
            val objectPath: ObjectPath = entry.key
            if (objectPath.name == ObjectName.main && objectPath.nesting.isRoot()) {
                continue
            }

            val objectLocation = ObjectLocation(props.documentPath, objectPath)
            val info = CustomObjectInfo.derive(objectLocation, graphStructure, exportsState.membership)

            CustomObject::class.react {
                this.objectLocation = objectLocation
                this.info = info
                this.customCommander = props.customCommander
                this.attributeEditorManager = props.attributeEditorManager

                this.indexInDocument = index
                this.dropMarker = CustomDragDrop.dropMarkerFor(
                    state.dragSourceIndex, state.dragOverIndex, state.dropAfter, index)
                this.dragHandlers = this@CustomView.dragHandlers
            }
        }

        CustomCreate::class.react {
            this.documentPath = props.documentPath
            this.documentNotation = props.serverNotation
            this.prototypes = CustomConventions.listPrototypes(graphStructure.graphNotation)
        }
    }
}
