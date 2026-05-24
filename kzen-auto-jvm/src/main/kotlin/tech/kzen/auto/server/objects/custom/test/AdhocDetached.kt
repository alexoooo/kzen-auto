package tech.kzen.auto.server.objects.custom.test

import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class AdhocDetached(
    private val named: AdhocNamed
): DetachedAction {
    override suspend fun execute(
        request: ExecutionRequest
    ): ExecutionResult {
        val name = named.name()
        return ExecutionResult.success(
            ExecutionValue.of("Hello: $name"))
    }
}