package tech.kzen.auto.server.objects

import tech.kzen.auto.common.api.AutoAction
import tech.kzen.auto.server.service.ServerContext


class GoTo(
        var location: String
) : AutoAction {
    override fun perform() {
        ServerContext.webDriverContext.get().get(location)
    }
}