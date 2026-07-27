package tech.kzen.auto.server.objects.job.channel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import tech.kzen.auto.common.paradigm.job.api.ChannelClient
import tech.kzen.auto.common.paradigm.job.api.ChannelServer
import tech.kzen.auto.common.paradigm.job.api.ChannelServerIterator
import tech.kzen.auto.common.paradigm.job.api.ServedRequest
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.atomic.AtomicInteger


/**
 * Duplex (request/reply) counterpart of [JobChannel]: a thin pairing of a request stream with a per-request
 * one-shot reply. Client endpoints [ChannelClient.request] a value and suspend until the single serving
 * endpoint ([server], an "actor" Worker) replies. Each request carries its own [CompletableDeferred], so
 * concurrent in-flight requests are correlated to their own responses — no serialization or correlation ids.
 *
 * Lifecycle mirrors [JobChannel]'s close-on-last-producer: the request stream closes (ending the server's
 * `for (served in serve)` loop) only once every client endpoint has closed (close-on-last-client).
 *
 * Non-generic (element type erased to `Any?`) so `@Reflect` instantiates it cleanly, like [JobChannel].
 *
 * [external] marks a channel whose *client* side is the UI bridge rather than a Worker:
 * [tech.kzen.auto.server.exec.job.JobRun] opens a client at launch and routes inbound
 * `ExecutionRequest`s to the serving Worker. The flag only rides on the instance for the bridge to read; the
 * channel itself treats external and internal duplex channels identically.
 */
@Reflect
class DuplexJobChannel(
    capacity: Int,
    val external: Boolean
) {
    //-----------------------------------------------------------------------------------------------------------------
    private class Pending(
        val request: Any?,
        val response: CompletableDeferred<Any?>
    )


    //-----------------------------------------------------------------------------------------------------------------
    private val requests: Channel<Pending> =
        if (capacity <= 0) {
            Channel(Channel.RENDEZVOUS)
        }
        else {
            Channel(capacity)
        }

    private val openClients = AtomicInteger(0)

    val server: ChannelServer<Any?, Any?> = Server()


    //-----------------------------------------------------------------------------------------------------------------
    fun newClient(): ChannelClient<Any?, Any?> {
        openClients.incrementAndGet()
        return Client()
    }


    private fun closeOneClient() {
        if (openClients.decrementAndGet() <= 0) {
            requests.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private inner class Client: ChannelClient<Any?, Any?> {
        private var closed = false

        override suspend fun request(request: Any?): Any? {
            val response = CompletableDeferred<Any?>()
            requests.send(Pending(request, response))
            return response.await()
        }

        override fun close() {
            if (!closed) {
                closed = true
                closeOneClient()
            }
        }
    }


    private inner class Server: ChannelServer<Any?, Any?> {
        override suspend fun receive(): ServedRequest<Any?, Any?>? {
            val pending = requests.receiveCatching().getOrNull()
                ?: return null
            return Served(pending)
        }

        override operator fun iterator(): ChannelServerIterator<Any?, Any?> {
            val delegate = requests.iterator()
            return object: ChannelServerIterator<Any?, Any?> {
                override suspend fun hasNext(): Boolean {
                    return delegate.hasNext()
                }

                override fun next(): ServedRequest<Any?, Any?> {
                    return Served(delegate.next())
                }
            }
        }
    }


    private class Served(
        private val pending: Pending
    ): ServedRequest<Any?, Any?> {
        override val request: Any?
            get() = pending.request

        override fun reply(response: Any?) {
            pending.response.complete(response)
        }
    }
}
