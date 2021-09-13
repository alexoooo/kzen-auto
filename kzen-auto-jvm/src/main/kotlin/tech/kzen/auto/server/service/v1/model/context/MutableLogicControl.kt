package tech.kzen.auto.server.service.v1.model.context

import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionRequest
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.model.LogicCommand
import java.util.concurrent.atomic.AtomicReference


class MutableLogicControl(
//    private val arguments: TupleValue
):
    LogicControl,
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
//    private class RequestPromise(
//        val request: ExecutionRequest,
//        val promise: CompletableFuture<ExecutionResult>
//    )


    //-----------------------------------------------------------------------------------------------------------------
    private val command = AtomicReference(LogicCommand.None)
//    private val requests = ConcurrentLinkedDeque<RequestPromise>()
    private val requestSubscriber = AtomicReference<(ExecutionRequest) -> ExecutionResult>()


    //-----------------------------------------------------------------------------------------------------------------
    fun commandCancel(): Boolean {
        return command.compareAndSet(LogicCommand.None, LogicCommand.Cancel)
    }


    fun commandPause(): Boolean {
        return command.compareAndSet(LogicCommand.None, LogicCommand.Pause)
    }


//    fun publishRequest(request: ExecutionRequest): Future<ExecutionResult> {
    fun publishRequest(request: ExecutionRequest): ExecutionResult {
        val subscriber = requestSubscriber.get()
            ?: return ExecutionResult.failure("No request listener")

        return try {
            subscriber(request)
        }
        catch (e: Throwable) {
            return ExecutionFailure.ofException(e)
        }

//        val promise = CompletableFuture<ExecutionResult>()
//        val requestPromise = RequestPromise(request, promise)
//        requests.add(requestPromise)
//        return promise
//        return response
    }


    //-----------------------------------------------------------------------------------------------------------------
//    override fun arguments(): TupleValue {
//        return arguments
//    }


    override fun pollCommand(): LogicCommand {
        return command.get()
    }


    override fun subscribeRequest(subscriber: (ExecutionRequest) -> ExecutionResult) {
        val wasSet = requestSubscriber.compareAndSet(null, subscriber)
        check(wasSet) { "Already subscribed" }

//        val nextRequestPromise = requests.poll()
//            ?: return
//
//        try {
//            val value = observer(nextRequestPromise.request)
//            nextRequestPromise.promise.complete(value)
//        }
//        catch (e: Throwable) {
//            nextRequestPromise.promise.complete(ExecutionFailure.ofException(e))
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun close() {
//        while (true) {
//            val next = requests.poll()
//                ?: return
//
//            next.promise.cancel(true)
//        }
    }
}