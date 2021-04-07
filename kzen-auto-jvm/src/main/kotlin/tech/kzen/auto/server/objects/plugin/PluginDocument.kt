package tech.kzen.auto.server.objects.plugin

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class PluginDocument(
    val jarPath: String
):
    DocumentArchetype(),
    DetachedAction
{
    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: DetachedRequest): ExecutionResult {
        return ExecutionSuccess.empty
    }
}