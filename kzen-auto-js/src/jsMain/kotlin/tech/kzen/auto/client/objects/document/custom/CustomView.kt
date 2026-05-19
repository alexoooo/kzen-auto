package tech.kzen.auto.client.objects.document.custom

import react.ChildrenBuilder
import react.Props
import react.State
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftObjectCommand
import tech.kzen.lib.common.service.notation.NotationConventions


//---------------------------------------------------------------------------------------------------------------------
external interface CustomViewProps: Props {
    var documentPath: DocumentPath
    var clientState: ClientState
    var serverNotation: DocumentObjectNotation
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
    override fun CustomViewState.init(props: CustomViewProps) {
        dragSourceIndex = null
        dragOverIndex = null
        dropAfter = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun mainObjectLocation(): ObjectLocation =
        ObjectLocation(props.documentPath, NotationConventions.mainObjectPath)


    private fun exportsListEntries(): List<ScalarAttributeNotation> {
        val mainNotation = props.serverNotation.notations[NotationConventions.mainObjectPath]
        val exportsListAttribute = mainNotation?.get(CustomConventions.exportsListAttributeName) as? ListAttributeNotation
        return exportsListAttribute?.values.orEmpty().filterIsInstance<ScalarAttributeNotation>()
    }


    private fun exportMembership(): Map<ObjectLocation, ScalarAttributeNotation> {
        val graphNotation = props.clientState.graphStructure().graphNotation
        val mainReferenceHost = ObjectReferenceHost.ofLocation(mainObjectLocation())
        return exportsListEntries().associateBy { entry ->
            graphNotation.coalesce.locate(ObjectReference.parse(entry.value), mainReferenceHost)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onToggleExport(objectLocation: ObjectLocation) {
        async {
            val existingEntry = exportMembership()[objectLocation]
            val command = if (existingEntry != null) {
                RemoveListItemInAttributeCommand(
                    mainObjectLocation(),
                    CustomConventions.exportsListAttributePath,
                    existingEntry,
                    false)
            }
            else {
                InsertListItemInAttributeCommand(
                    mainObjectLocation(),
                    CustomConventions.exportsListAttributePath,
                    PositionRelation.at(exportsListEntries().size),
                    ScalarAttributeNotation(objectLocation.objectPath.asString()))
            }
            ClientContext.mirroredGraphStore.apply(command)
        }
    }


    private fun onDeleteObject(objectLocation: ObjectLocation) {
        async {
            val existingEntry = exportMembership()[objectLocation]
            if (existingEntry != null) {
                ClientContext.mirroredGraphStore.apply(RemoveListItemInAttributeCommand(
                    mainObjectLocation(),
                    CustomConventions.exportsListAttributePath,
                    existingEntry,
                    false))
            }
            ClientContext.mirroredGraphStore.apply(RemoveObjectCommand(objectLocation))
        }
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
        val source = state.dragSourceIndex
        val target = state.dragOverIndex
        val dropAfter = state.dropAfter

        setState {
            dragSourceIndex = null
            dragOverIndex = null
            this.dropAfter = false
        }

        if (source == null || target == null) {
            return
        }

        val rawTarget = if (dropAfter) target + 1 else target
        val newIndex = if (rawTarget > source) rawTarget - 1 else rawTarget
        if (newIndex == source) {
            return
        }

        val sourceObjectPath = props.serverNotation.notations.map.keys.toList().getOrNull(source)
            ?: return

        async {
            ClientContext.mirroredGraphStore.apply(ShiftObjectCommand(
                ObjectLocation(props.documentPath, sourceObjectPath),
                PositionRelation.at(newIndex)))
        }
    }


    private fun dropMarkerFor(index: Int): DropMarker? {
        val source = state.dragSourceIndex ?: return null
        if (state.dragOverIndex != index) {
            return null
        }
        val dropAfter = state.dropAfter
        val rawTarget = if (dropAfter) index + 1 else index
        val newIndex = if (rawTarget > source) rawTarget - 1 else rawTarget
        if (newIndex == source) {
            return null
        }
        return if (dropAfter) DropMarker.Below else DropMarker.Above
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val graphStructure = props.clientState.graphStructure()
        val graphMetadata = graphStructure.graphMetadata
        val graphNotation = graphStructure.graphNotation
        val membership = exportMembership()

        for ((index, entry) in props.serverNotation.notations.map.entries.withIndex()) {
            val objectPath: ObjectPath = entry.key
            if (objectPath.name == ObjectName.main && objectPath.nesting.isRoot()) {
                continue
            }

            val objectLocation = ObjectLocation(props.documentPath, objectPath)
            val objectMetadata = graphMetadata.objectMetadata[objectLocation]
            val isAbstract = graphNotation
                .directAttribute(objectLocation, NotationConventions.abstractAttributePath)
                ?.asBoolean()
                ?: false
            val isLogic = objectMetadata?.tags?.contains(CustomConventions.logicTag) ?: false
            val isExported = objectLocation in membership

            val toggleExportHandler: (() -> Unit)? =
                if (isAbstract) null
                else { { onToggleExport(objectLocation) } }

            val deleteHandler: () -> Unit = { onDeleteObject(objectLocation) }

            CustomObject::class.react {
                this.objectPath = objectPath
                this.objectLocation = objectLocation
                this.objectMetadata = objectMetadata
                this.isAbstract = isAbstract
                this.isLogic = isLogic
                this.isExported = isExported
                this.onToggleExport = toggleExportHandler
                this.onDelete = deleteHandler
                this.attributeEditorManager = props.attributeEditorManager

                this.indexInDocument = index
                this.dropMarker = dropMarkerFor(index)
                this.onDragStart = { source -> onCardDragStart(source) }
                this.onDragOver = { target, after -> onCardDragOver(target, after) }
                this.onDragEnd = { onCardDragEnd() }
                this.onDrop = { onCardDrop() }
            }
        }

        CustomCreate::class.react {
            this.documentPath = props.documentPath
            this.documentNotation = props.serverNotation
            this.prototypes = CustomConventions.listPrototypes(graphNotation)
        }
    }
}
