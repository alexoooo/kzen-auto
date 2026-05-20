package tech.kzen.auto.client.objects.document.custom.view

import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftObjectCommand
import tech.kzen.lib.common.service.notation.NotationConventions


//---------------------------------------------------------------------------------------------------------------------
class CustomCommander(
    private val documentPathProvider: () -> DocumentPath?,
    private val serverNotationProvider: () -> DocumentObjectNotation?
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun toggleExport(objectLocation: ObjectLocation) {
        val ctx = snapshot()
        check(!CustomObjectInfo.isAbstract(objectLocation, ctx.graphStructure)) {
            "Cannot toggle export on abstract object: $objectLocation"
        }
        val mainObjectLocation = ObjectLocation(ctx.documentPath, NotationConventions.mainObjectPath)
        val exports = CustomExports.current(ctx.serverNotation, ctx.graphStructure, mainObjectLocation)
        dispatch(listOf(buildToggleExportCommand(objectLocation, mainObjectLocation, exports)))
    }


    fun deleteObject(objectLocation: ObjectLocation) {
        check(objectLocation.objectPath != NotationConventions.mainObjectPath) {
            "Cannot delete the main object: $objectLocation"
        }
        val ctx = snapshot()
        val mainObjectLocation = ObjectLocation(ctx.documentPath, NotationConventions.mainObjectPath)
        val exports = CustomExports.current(ctx.serverNotation, ctx.graphStructure, mainObjectLocation)
        dispatch(buildDeleteCommands(objectLocation, mainObjectLocation, exports))
    }


    fun shiftObject(dragSourceIndex: Int?, dragOverIndex: Int?, dropAfter: Boolean) {
        val source = dragSourceIndex ?: return
        val target = dragOverIndex ?: return
        val rawTarget = if (dropAfter) target + 1 else target
        val newIndex = if (rawTarget > source) rawTarget - 1 else rawTarget
        if (newIndex == source) {
            return
        }

        val ctx = snapshot()
        val sourceObjectPath = ctx.serverNotation.notations.map.keys.toList().getOrNull(source)
            ?: error("Drag source index $source out of range")
        dispatch(listOf(ShiftObjectCommand(
            ObjectLocation(ctx.documentPath, sourceObjectPath),
            PositionRelation.at(newIndex))))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private data class Snapshot(
        val documentPath: DocumentPath,
        val serverNotation: DocumentObjectNotation,
        val graphStructure: GraphStructure)


    private fun snapshot(): Snapshot {
        val documentPath = documentPathProvider()
            ?: error("CustomCommander: documentPath unavailable")
        val serverNotation = serverNotationProvider()
            ?: error("CustomCommander: serverNotation unavailable")
        val graphStructure = ClientContext.clientStateGlobal.current()?.graphStructure()
            ?: error("CustomCommander: graphStructure unavailable")
        return Snapshot(documentPath, serverNotation, graphStructure)
    }


    private fun dispatch(commands: List<NotationCommand>) {
        async {
            for (command in commands) {
                ClientContext.mirroredGraphStore.apply(command)
            }
        }
    }


    private fun buildToggleExportCommand(
        objectLocation: ObjectLocation,
        mainObjectLocation: ObjectLocation,
        exports: CustomExportsState
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
        exports: CustomExportsState
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
}
