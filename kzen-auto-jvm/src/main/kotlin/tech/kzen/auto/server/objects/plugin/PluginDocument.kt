package tech.kzen.auto.server.objects.plugin

import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.server.context.PluginAvailability
import tech.kzen.auto.server.context.runtime.KzenAutoRuntime
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/**
 * The Plugin document: a cached, read-only view of the installed plugin universe — the runtime's scopes and
 * what each contributed, the classes this workspace resolved and their availability here, and every named
 * failure ([PluginUniverseView]). Installation is a filesystem act (a folder under `--plugin.root=`, applied at
 * the next start); this document changes nothing and carries no attributes of its own (the former `jarPath`
 * Report-definer jar retired in HS21; a stale `jarPath:` line in an old document is ignored).
 */
@Reflect
class PluginDocument(
    @Suppress("unused") private val selfLocation: ObjectLocation,
    @Service private val runtime: KzenAutoRuntime,
    @Service private val pluginAvailability: PluginAvailability
):
    DocumentArchetype(),
    DetachedAction
{
    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        val scopes = PluginUniverseView.scopes(runtime, pluginAvailability)
        return ExecutionSuccess(
            ExecutionValue.of(scopes.map { it.asCollection() }),
            NullExecutionValue)
    }
}
