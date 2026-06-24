package tech.kzen.auto.server.objects.job.channel

import kotlinx.coroutines.channels.Channel
import tech.kzen.auto.common.paradigm.job.api.ChannelInput
import tech.kzen.auto.common.paradigm.job.api.ChannelInputIterator
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.lib.common.reflect.Reflect
import java.util.concurrent.atomic.AtomicInteger


/**
 * The shared, first-class conduit a Job `Channel` notation object instantiates: one-way streaming over a
 * [kotlinx.coroutines.channels.Channel], with the consumer endpoint exposed as [input] and each producer
 * endpoint handed out by [newProducer].
 *
 * Element type is erased (`Any?`) at runtime — the declared `of` type is for authoring/wiring only — so a
 * single non-generic class instantiates cleanly via `@Reflect`; the typed [ChannelInput] / [ChannelOutput]
 * views are what workers see (injected by `JobChannelCreator`).
 *
 * **Close-on-last-producer:** a fan-in channel may have several producer endpoints; consumers see
 * end-of-stream only once *all* of them have [ChannelOutput.close]d, so one producer finishing is not a
 * premature EOF. The live producer count is tracked across worker threads via an [AtomicInteger].
 */
@Reflect
class JobChannel(
    buffer: Int
) {
    //-----------------------------------------------------------------------------------------------------------------
    private val channel: Channel<Any?> =
        if (buffer <= 0) {
            Channel(Channel.RENDEZVOUS)
        }
        else {
            Channel(buffer)
        }

    private val openProducers = AtomicInteger(0)


    //-----------------------------------------------------------------------------------------------------------------
    val input: ChannelInput<Any?> = Input()


    fun newProducer(): ChannelOutput<Any?> {
        openProducers.incrementAndGet()
        return Producer()
    }


    private fun closeOneProducer() {
        if (openProducers.decrementAndGet() <= 0) {
            channel.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private inner class Producer: ChannelOutput<Any?> {
        private var closed = false

        override suspend fun send(payload: Any?) {
            channel.send(payload)
        }

        override fun close() {
            if (! closed) {
                closed = true
                closeOneProducer()
            }
        }
    }


    private inner class Input: ChannelInput<Any?> {
        override suspend fun receive(): Any? {
            return channel.receiveCatching().getOrNull()
        }

        override operator fun iterator(): ChannelInputIterator<Any?> {
            val delegate = channel.iterator()
            return object: ChannelInputIterator<Any?> {
                override suspend fun hasNext(): Boolean {
                    return delegate.hasNext()
                }

                override fun next(): Any? {
                    return delegate.next()
                }
            }
        }
    }
}
