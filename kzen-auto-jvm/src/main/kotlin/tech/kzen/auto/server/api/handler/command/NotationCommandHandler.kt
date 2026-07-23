package tech.kzen.auto.server.api.handler.command

import kotlinx.coroutines.runBlocking
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.service.store.DirectGraphStore
import tech.kzen.lib.common.util.digest.Digest


// The command methods are extension functions grouped by concern in the sibling Notation*Commands.kt files;
// each applies its command through applyCommand. yamlNotationParser and applyCommand are internal so those
// same-module files can reach them; graphStore stays private since only applyCommand touches it.
class NotationCommandHandler(
    private val graphStore: DirectGraphStore,
    internal val yamlNotationParser: NotationParser
) {
    //-----------------------------------------------------------------------------------------------------------------
    internal fun applyCommand(command: NotationCommand): Digest {
        return runBlocking {
            graphStore.apply(command)
            graphStore.digest()
        }
    }
}
