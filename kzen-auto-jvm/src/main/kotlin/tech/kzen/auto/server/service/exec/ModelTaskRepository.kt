package tech.kzen.auto.server.service.exec

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.common.paradigm.task.api.ManagedTask
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskModel
import tech.kzen.auto.common.paradigm.task.model.TaskState
import tech.kzen.auto.common.paradigm.task.service.TaskRepository
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.store.LocalGraphStore
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean


class ModelTaskRepository(
    private val graphStore: LocalGraphStore,
    private val graphCreator: GraphCreator
):
    TaskRepository
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val maxTerminated = 64
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val active = mutableMapOf<TaskId, ActiveHandle>()
    private val terminated = mutableMapOf<TaskId, TaskModel>()


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun submit(taskLocation: ObjectLocation, request: DetachedRequest): TaskModel {
        val graphDefinition = graphStore
            .graphDefinition()
            .transitiveSuccessful()
            .filterDefinitions(AutoConventions.serverAllowed)

        // TODO: add GraphInstanceAttempt for error reporting
        val graphInstance =
            graphCreator.createGraph(graphDefinition)

        val instance = graphInstance.objectInstances[taskLocation]?.reference
            ?: throw IllegalArgumentException("Not found: $taskLocation")

        val task = instance as ManagedTask

        val taskId = TaskId(Instant.now().toString())

        val handle = ActiveHandle(
//            task,
            TaskModel(
                taskId,
                taskLocation,
                request,
                TaskState.Running,
                null,
                null
            ))

        active[taskId] = handle

        task.start(request, handle)

        return handle.model
    }


    override suspend fun cancel(taskId: TaskId): TaskModel? {
        val handle = active.remove(taskId)
            ?: return terminated[taskId]

        handle.requestCancel()
        handle.awaitTerminal()

        return handle.model
    }


    override suspend fun lookupActive(taskLocation: ObjectLocation): Set<TaskId> {
        return active
            .values
            .map { it.model }
            .filter { it.taskLocation == taskLocation }
            .map { it.taskId }
            .toSet()
    }


    override suspend fun query(taskId: TaskId): TaskModel? {
        return active[taskId]?.model
            ?: terminated[taskId]
    }


    private fun addTerminated(taskModel: TaskModel) {
        terminated[taskModel.taskId] = taskModel

        while (terminated.size > maxTerminated) {
            val iterator = terminated.iterator()
            iterator.next()
            iterator.remove()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private inner class ActiveHandle(
//        val task: ManagedTask,
        var model: TaskModel
    ): TaskHandle {
        private var cancelRequested = AtomicBoolean(false)
        private val completeLatch = CountDownLatch(1)


        override fun complete(result: ExecutionResult) {
            model = model.copy(
                finalResult = result,
                state = TaskState.Done)

            terminate()
        }


        override fun completeCancelled() {
            model = model.copy(
                state = TaskState.Cancelled)

            terminate()
        }


        override fun update(partialResult: ExecutionSuccess) {
            model = model.copy(
                partialResult = partialResult)
        }


        override fun cancelRequested(): Boolean {
            return cancelRequested.get()
        }


        private fun terminate() {
            active.remove(model.taskId)
            addTerminated(model)

            completeLatch.countDown()
        }


        fun requestCancel() {
            cancelRequested.set(true)
        }


        fun awaitTerminal() {
            completeLatch.await()
        }
    }
}