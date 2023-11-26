package tech.kzen.auto.server.service.v1.model.context

import tech.kzen.auto.server.service.v1.LogicControl
import tech.kzen.auto.server.service.v1.model.LogicCommand
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
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
        val previousCommand = command.getAndSet(LogicCommand.Cancel)
        return previousCommand != LogicCommand.Cancel
    }


    fun commandPause(): Boolean {
        return command.compareAndSet(LogicCommand.None, LogicCommand.Pause)
    }


    fun commandUnpause(): Boolean {
        return command.compareAndSet(LogicCommand.Pause, LogicCommand.None)
    }


    fun publishRequest(request: ExecutionRequest): ExecutionResult {
        val subscriber = requestSubscriber.get()
            ?: return ExecutionResult.failure("No request listener")

        return try {
            subscriber(request)
        }
        catch (e: Throwable) {
            return ExecutionFailure.ofException(e)
        }
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