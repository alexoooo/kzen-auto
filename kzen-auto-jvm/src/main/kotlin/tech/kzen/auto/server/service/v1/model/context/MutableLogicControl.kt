package tech.kzen.auto.server.service.v1.model.context

import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.model.LogicCommand
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference


class MutableLogicControl(
//    private val arguments: TupleValue
):
    LogicControl,
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    private class RequestPromise(
        val request: ExecutionRequest,
        val promise: CompletableFuture<ExecutionResult>
    )


    //-----------------------------------------------------------------------------------------------------------------
    private val command = AtomicReference(LogicCommand.None)
    private val requests = ConcurrentLinkedDeque<RequestPromise>()


    //-----------------------------------------------------------------------------------------------------------------
    fun commandCancel(): Boolean {
        return command.compareAndSet(LogicCommand.None, LogicCommand.Cancel)
    }


    fun commandPause(): Boolean {
        return command.compareAndSet(LogicCommand.None, LogicCommand.Pause)
    }


    fun addRequest(request: ExecutionRequest): Future<ExecutionResult> {
        val promise = CompletableFuture<ExecutionResult>()
        val requestPromise = RequestPromise(request, promise)
        requests.add(requestPromise)
        return promise
    }


    //-----------------------------------------------------------------------------------------------------------------
//    override fun arguments(): TupleValue {
//        return arguments
//    }


    override fun pollCommand(): LogicCommand {
        return command.get()
    }


    override fun pollRequest(observer: (ExecutionRequest) -> ExecutionResult) {
        val nextRequestPromise = requests.poll()
            ?: return

        val value = observer(nextRequestPromise.request)

        nextRequestPromise.promise.complete(value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
        while (true) {
            val next = requests.poll()
                ?: return

            next.promise.cancel(true)
        }
    }
}