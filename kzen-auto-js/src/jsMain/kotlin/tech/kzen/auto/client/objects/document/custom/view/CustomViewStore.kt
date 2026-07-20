package tech.kzen.auto.client.objects.document.custom.view

import tech.kzen.auto.client.objects.document.common.dragdrop.computeDropIndex
import tech.kzen.auto.client.objects.document.custom.model.CustomState
import tech.kzen.auto.client.objects.document.custom.model.CustomStore
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.service.rest.ClientRestTaskRepository
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.auto.common.objects.document.custom.model.CustomObjectInfo
import tech.kzen.auto.common.objects.document.custom.model.CustomViewExports
import tech.kzen.auto.common.objects.document.custom.model.CustomViewExportsState
import tech.kzen.auto.common.objects.document.custom.model.CustomViewReorder
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.service.store.MirroredGraphSuccess
import tech.kzen.lib.common.util.naming.NextAvailableName


class CustomViewStore(
    private val parent: CustomStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    val restClient: ClientRestApi get() = parent.restClient
    val clientRestTaskRepository: ClientRestTaskRepository get() = parent.clientRestTaskRepository


    //-----------------------------------------------------------------------------------------------------------------
    fun toggleExport(objectLocation: ObjectLocation) {
        val snapshot = snapshot()
        check(!CustomObjectInfo.isAbstract(objectLocation, snapshot.graphStructure)) {
            "Cannot toggle export on abstract object: $objectLocation"
        }
        val mainObjectLocation = ObjectLocation(snapshot.state.documentPath, NotationConventions.mainObjectPath)
        val exports = CustomViewExports.current(snapshot.state.serverNotation, snapshot.graphStructure, mainObjectLocation)
        dispatch(listOf(buildToggleExportCommand(objectLocation, mainObjectLocation, exports)))
    }


    fun deleteObject(objectLocation: ObjectLocation) {
        check(objectLocation.objectPath != NotationConventions.mainObjectPath) {
            "Cannot delete the main object: $objectLocation"
        }
        val snapshot = snapshot()
        val mainObjectLocation = ObjectLocation(snapshot.state.documentPath, NotationConventions.mainObjectPath)
        val exports = CustomViewExports.current(snapshot.state.serverNotation, snapshot.graphStructure, mainObjectLocation)
        dispatch(buildDeleteCommands(objectLocation, mainObjectLocation, exports))
    }


    fun createObject(prototype: ObjectLocation, onResult: (String?) -> Unit) {
        val snapshot = snapshot()
        val newName = nextAvailableObjectName(snapshot.state, prototype.objectPath.name)
        val newPath = NotationConventions.mainObjectPath.nest(
            CustomConventions.objectsAttributePath, newName)
        val newLocation = ObjectLocation(snapshot.state.documentPath, newPath)
        val endOfDocument = PositionRelation.at(snapshot.state.serverNotation.notations.map.size)
        val command = AddObjectCommand.ofParent(newLocation, endOfDocument, prototype.objectPath.name)

        async {
            val result = parent.mirroredGraphStore.apply(command)
            when (result) {
                is MirroredGraphSuccess -> onResult(null)
                is MirroredGraphError -> onResult(result.error.message ?: result.error.toString())
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun onDragStart(sourceIndex: Int) {
        parent.update {
            it.withView { view ->
                view.copy(dragSourceIndex = sourceIndex, dragOverIndex = null, dropAfter = false)
            }
        }
    }


    fun onDragOver(targetIndex: Int, dropAfter: Boolean) {
        val current = parent.stateOrNull()?.view
            ?: return
        if (current.dragSourceIndex == null) {
            return
        }
        if (current.dragOverIndex == targetIndex && current.dropAfter == dropAfter) {
            return
        }
        parent.update {
            it.withView { view -> view.copy(dragOverIndex = targetIndex, dropAfter = dropAfter) }
        }
    }


    fun onDragEnd() {
        val current = parent.stateOrNull()?.view
            ?: return
        if (current.dragSourceIndex == null && current.dragOverIndex == null) {
            return
        }
        parent.update {
            it.withView { CustomViewState() }
        }
    }


    fun onDrop() {
        val snapshot = snapshot()
        val view = snapshot.state.view
        val source = view.dragSourceIndex
        val target = view.dragOverIndex
        val dropAfter = view.dropAfter

        if (source != null && target != null) {
            // NB: drag indices are view-indices (orderedEntries skips the root 'main' object);
            //     CustomViewReorder translates to a doc-index against the unfiltered notation map before shifting.
            val allDocPaths = snapshot.state.serverNotation.notations.map.keys.toList()
            val newViewIndex = computeDropIndex(source, target, dropAfter)
            val dropShift = CustomViewReorder.dropShift(allDocPaths, source, newViewIndex)

            if (dropShift != null) {
                dispatch(listOf(ShiftObjectCommand(
                    ObjectLocation(snapshot.state.documentPath, dropShift.sourcePath),
                    PositionRelation.at(dropShift.newDocPosition))))
            }
        }

        parent.update { it.withView { CustomViewState() } }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private data class Snapshot(
        val state: CustomState,
        val graphStructure: GraphStructure)


    private fun snapshot(): Snapshot {
        val state = parent.stateOrNull()
            ?: error("CustomViewStore: state unavailable")
        val graphStructure = parent.clientStateGlobal.current()?.graphStructure()
            ?: error("CustomViewStore: graphStructure unavailable")
        return Snapshot(state, graphStructure)
    }


    private fun dispatch(commands: List<NotationCommand>) {
        async {
            for (command in commands) {
                parent.mirroredGraphStore.apply(command)
            }
        }
    }


    private fun buildToggleExportCommand(
        objectLocation: ObjectLocation,
        mainObjectLocation: ObjectLocation,
        exports: CustomViewExportsState
    ): NotationCommand {
        val existingEntry = exports.membership[objectLocation]
        return if (existingEntry != null) {
            RemoveListItemInAttributeCommand(
                mainObjectLocation,
                CustomConventions.exportsListAttributePath,
                existingEntry,
                false)
        }
        else {
            InsertListItemInAttributeCommand(
                mainObjectLocation,
                CustomConventions.exportsListAttributePath,
                PositionRelation.at(exports.entries.size),
                ScalarAttributeNotation(objectLocation.objectPath.asString()))
        }
    }


    private fun buildDeleteCommands(
        objectLocation: ObjectLocation,
        mainObjectLocation: ObjectLocation,
        exports: CustomViewExportsState
    ): List<NotationCommand> {
        val existingEntry = exports.membership[objectLocation]
        val commands = mutableListOf<NotationCommand>()
        if (existingEntry != null) {
            commands.add(RemoveListItemInAttributeCommand(
                mainObjectLocation,
                CustomConventions.exportsListAttributePath,
                existingEntry,
                false))
        }
        commands.add(RemoveObjectCommand(objectLocation))
        return commands
    }


    private fun nextAvailableObjectName(state: CustomState, prototypeName: ObjectName): ObjectName {
        val attributePath = CustomConventions.objectsAttributePath
        val taken: Set<String> = state.serverNotation.notations.map.keys
            .filter {
                it.nesting.segments.size == 1 &&
                    it.nesting.segments.first().objectName == ObjectName.main &&
                    it.nesting.segments.first().attributePath == attributePath
            }
            .map { it.name.value }
            .toSet()

        val chosen = NextAvailableName.find(prototypeName.value) { it !in taken }
            ?: "${prototypeName.value}${state.serverNotation.notations.map.size + 1}"

        return ObjectName(chosen)
    }
}
