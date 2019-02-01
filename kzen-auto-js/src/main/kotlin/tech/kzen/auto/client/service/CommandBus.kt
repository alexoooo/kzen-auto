package tech.kzen.auto.client.service

import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.notation.edit.*
import tech.kzen.lib.common.notation.io.NotationParser
import tech.kzen.lib.common.notation.repo.NotationRepository
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.IoUtils


// TODO: move to lib?
class CommandBus(
        private val clientRepository: NotationRepository,
        private val modelManager: ModelManager,
        private val restClient: ClientRestApi,
        private val notationParser: NotationParser
) {
    //-----------------------------------------------------------------------------------------------------------------
    // TODO: observer vs subscriber?
    interface Observer {
//    fun onCommandRequest(command: ProjectCommand)
//
//    fun onCommandAppliedInClient(command: ProjectCommand, event: ProjectEvent)
        fun onCommandFailedInClient(command: NotationCommand, cause: Throwable)
        fun onCommandSuccess(command: NotationCommand, event: NotationEvent)

//    fun onCommandEventHandledLocally(command: ProjectCommand, event: ProjectEvent)
//    fun onCommandAppliedInServer(command: ProjectCommand, event: ProjectEvent)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()


    fun observe(observer: Observer) {
        observers.add(observer)
    }

    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    private fun onCommandFailedInClient(command: NotationCommand, cause: Throwable) {
        for (observer in observers) {
            observer.onCommandFailedInClient(command, cause)
        }
    }

    private fun onCommandSuccess(command: NotationCommand, event: NotationEvent) {
        for (observer in observers) {
            observer.onCommandSuccess(command, event)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun apply(command: NotationCommand) {
        println("CommandBus - applying command: $command")
//        observer.onCommandRequest(command)

        val event = try {
            clientRepository.apply(command)
        }
        catch (e: Throwable) {
            console.log("CommandBus - caught error in client: $command", e)
            onCommandFailedInClient(command, e)
            return
        }

        println("CommandBus - client event: $event")

        val bundleValue = ClientContext.notationMediaCache.read(NotationConventions.mainPath)
        println("CommandBus - new bundle value !!: ${IoUtils.utf8ToString(bundleValue)}")

        val parsedBundle = ClientContext.notationParser.parseBundle(bundleValue)
        println("CommandBus - parsedBundle: $parsedBundle")


//        val bundleNotation = clientRepository.notation().bundleNotations.values[NotationConventions.mainPath]!!
//        val objectNotation = bundleNotation.objects.values.values.iterator().next()
//        println("CommandBus - first object: $objectNotation")


//        observer.onCommandAppliedInClient(command, event)

        val clientDigest = clientRepository.digest()
        println("CommandBus - applied in client: $clientDigest")

        modelManager.onEvent(event)
//        observer.onCommandEventHandledLocally(command, event)

        val restDigest = applyRest(command)
        println("CommandBus - applied in REST: $restDigest")

        if (clientDigest != restDigest) {
            modelManager.refresh()
        }

        onCommandSuccess(command, event)
    }


    private suspend fun applyRest(command: NotationCommand): Digest =
        when (command) {
            is CreateBundleCommand ->
                restClient.createBundle(
                        command.bundlePath)


            is DeleteBundleCommand ->
                restClient.deleteBundle(
                        command.bundlePath)


            is AddObjectCommand -> {
                val deparsed = notationParser.deparseObject(command.body)
                restClient.addObject(
                        command.objectLocation, command.indexInBundle, deparsed)
            }


            is RemoveObjectCommand ->
                restClient.removeObject(
                        command.objectLocation)


            is ShiftObjectCommand ->
                restClient.shiftObject(
                        command.objectLocation, command.newPositionInBundle)


            is RenameObjectCommand ->
                restClient.renameObject(
                        command.objectLocation, command.newName)


            is InsertObjectInListAttributeCommand -> {
                val deparsed = notationParser.deparseObject(command.body)
                restClient.insertObjectInList(
                        command.containingObjectLocation,
                        command.containingList,
                        command.indexInList,
                        command.objectName,
                        command.positionInBundle,
                        deparsed)
            }


            is UpsertAttributeCommand -> {
                val deparsed = notationParser.deparseAttribute(command.attributeNotation)
                restClient.upsertAttribute(
                        command.objectLocation, command.attributeName, deparsed)
            }


            is UpdateInAttributeCommand -> {
                val deparsed = notationParser.deparseAttribute(command.attributeNotation)
                restClient.updateInAttribute(
                        command.objectLocation, command.attributePath, deparsed)
            }


            is InsertListItemInAttributeCommand -> {
                val deparsed = notationParser.deparseAttribute(command.item)
                restClient.insertListItemInAttribute(
                        command.objectLocation, command.containingList, command.indexInList, deparsed)
            }


            is InsertMapEntryInAttributeCommand -> {
                val deparsed = notationParser.deparseAttribute(command.value)
                restClient.insertMapEntryInAttribute(
                        command.objectLocation, command.containingMap, command.indexInMap, command.mapKey, deparsed)
            }


            is RemoveInAttributeCommand ->
                restClient.removeInAttribute(
                        command.objectLocation, command.attributePath)


            is ShiftInAttributeCommand ->
                restClient.shiftInAttribute(
                        command.objectLocation, command.attributePath, command.newPosition)


            else ->
                throw UnsupportedOperationException("Unknown: $command")
        }
}