package tech.kzen.auto.server.objects.job.worker.content.scope

import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.objects.job.channel.DownstreamClosedException
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.reflect.Reflect


/**
 * `Take(n)` INSIDE an entry scope (content streaming spike CS2): forwards the first [count] elements the body
 * offers it and then declares itself closed by throwing [DownstreamClosedException] — the same signal a
 * completed channel consumer raises — so the scope stops reading, closes its archive cursor without draining,
 * and finishes normally. Body Workers ahead of it in the chain see the throw from their `emit.send`, exactly
 * as a Worker sees a closed output channel.
 */
@Reflect
class ScopeTake(
    private val count: Int
):
    ScopeBodyWorker
{
    private var taken = 0L


    override fun captureState(): Any =
        taken


    override fun loadState(state: Any?) {
        taken = state as Long
    }



    override suspend fun onElement(element: DataValue, emit: BodyEmitter, control: JobControl) {
        if (taken >= count) {
            throw closed()
        }
        emit.send(element)
        taken += 1
        if (taken >= count) {
            throw closed()
        }
    }


    private fun closed(): DownstreamClosedException =
        DownstreamClosedException("Take($count) has forwarded $taken elements and is complete")
}
