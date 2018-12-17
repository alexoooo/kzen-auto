package tech.kzen.auto.server.objects

import tech.kzen.auto.common.api.AutoAction
import tech.kzen.auto.server.service.ServerContext


@Suppress("unused")
class GoTo(
        var location: String
): AutoAction {
    override suspend fun perform() {
        ServerContext.webDriverContext.get().get(location)
    }
}