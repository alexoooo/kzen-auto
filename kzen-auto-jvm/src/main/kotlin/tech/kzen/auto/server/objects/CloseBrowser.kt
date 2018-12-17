package tech.kzen.auto.server.objects

import tech.kzen.auto.common.api.AutoAction
import tech.kzen.auto.server.service.ServerContext


@Suppress("unused")
class CloseBrowser: AutoAction {
    override suspend fun perform() {
        ServerContext.webDriverContext.quit()
    }
}