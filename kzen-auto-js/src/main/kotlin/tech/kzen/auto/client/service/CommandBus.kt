package tech.kzen.auto.client.service

import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.edit.*
import tech.kzen.lib.common.notation.io.NotationParser
import tech.kzen.lib.common.notation.repo.NotationRepository
import tech.kzen.lib.common.util.Digest


class CommandBus(
        private val clientRepository: NotationRepository,
        private val modelManager: ModelManager,
        private val restClient: RestClient,
        private val notationParser: NotationParser
) {
    //-----------------------------------------------------------------------------------------------------------------
    // TODO: observer vs subscriber?
    interface Observer {
//    fun onCommandRequest(command: ProjectCommand)
//
//    fun onCommandAppliedInClient(command: ProjectCommand, event: ProjectEvent)
        fun onCommandFailedInClient(command: ProjectCommand, cause: Throwable)
        fun onCommandSuccess(command: ProjectCommand, event: ProjectEvent)

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


    private fun onCommandFailedInClient(command: ProjectCommand, cause: Throwable) {
        for (observer in observers) {
            observer.onCommandFailedInClient(command, cause)
        }
    }

    private fun onCommandSuccess(command: ProjectCommand, event: ProjectEvent) {
        for (observer in observers) {
            observer.onCommandSuccess(command, event)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun apply(command: ProjectCommand) {
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


    private suspend fun applyRest(command: ProjectCommand): Digest =
        when (command) {
            is CreatePackageCommand ->
                restClient.createPackage(command.projectPath)

            is DeletePackageCommand ->
                restClient.deletePackage(command.projectPath)


            is AddObjectCommand -> {
                val deparsed = notationParser.deparseObject(command.body)
                restClient.addCommand(command.projectPath, command.objectName, deparsed)
            }

            is RemoveObjectCommand ->
                restClient.removeCommand(command.objectName)

            is ShiftObjectCommand ->
                restClient.shiftCommand(command.objectName, command.indexInPackage)

            is RenameObjectCommand ->
                restClient.renameCommand(command.objectName, command.newName)


            is EditParameterCommand -> {
                val deparsed = notationParser.deparseParameter(command.parameterValue)
                restClient.editCommand(command.objectName, command.parameterPath, deparsed)
            }


            else ->
                throw UnsupportedOperationException("Unknown: $command")
        }
}