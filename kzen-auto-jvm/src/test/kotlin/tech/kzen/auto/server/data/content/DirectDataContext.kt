package tech.kzen.auto.server.data.content

import tech.kzen.auto.common.data.api.DataContext


internal object DirectDataContext: DataContext {
    override fun argument(name: String): Any? = null

    override suspend fun <R> blocking(block: () -> R): R = block()
}
