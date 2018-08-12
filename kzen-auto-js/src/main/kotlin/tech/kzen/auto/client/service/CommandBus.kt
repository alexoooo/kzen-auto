package tech.kzen.auto.client.service

import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.edit.*
import tech.kzen.lib.common.notation.io.NotationParser
import tech.kzen.lib.common.notation.repo.NotationRepository
import tech.kzen.lib.common.util.Digest


class CommandBus(
        private var clientRepository: NotationRepository,
        private var modelManager: ModelManager,
        private var restClient: RestClient,
        private var notationParser: NotationParser
) {
    //-----------------------------------------------------------------------------------------------------------------
    suspend fun apply(command: ProjectCommand) {
        println("CommandBus - applying command: $command")

        val event = clientRepository.apply(command)
        val clientDigest = clientRepository.digest()
        println("CommandBus - applied in client: $clientDigest")

        modelManager.onEvent(event)

        val restDigest = applyRest(command)
        println("CommandBus - applied in REST: $restDigest")

        if (clientDigest != restDigest) {
            modelManager.refresh()
        }
    }


    private suspend fun applyRest(command: ProjectCommand): Digest =
        when (command) {
            is EditParameterCommand -> {
                val deparsed = notationParser.deparseParameter(command.parameterValue)
                restClient.editCommand(command.objectName, command.parameterPath, deparsed)
            }

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

            else ->
                throw UnsupportedOperationException("Unknown: $command")
        }
}