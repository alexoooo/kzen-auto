package tech.kzen.auto.server.objects.sequence

import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.paradigm.sequence.SequenceValidation
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class SequenceValidator: DetachedAction {
    override suspend fun execute(
        request: ExecutionRequest
    ): ExecutionResult {
        val sequenceValidation = SequenceValidation(mapOf())

        return ExecutionSuccess.ofValue(sequenceValidation.asExecutionValue())
    }
}