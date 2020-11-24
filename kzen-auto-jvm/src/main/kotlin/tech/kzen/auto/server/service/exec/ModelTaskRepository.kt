package tech.kzen.auto.server.service.exec

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.common.paradigm.task.api.ManagedTask
import tech.kzen.auto.common.paradigm.task.api.TaskHandle
import tech.kzen.auto.common.paradigm.task.api.TaskRun
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskModel
import tech.kzen.auto.common.paradigm.task.model.TaskState
import tech.kzen.auto.common.paradigm.task.service.TaskRepository
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.cqrs.DeletedDocumentEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedDocumentRefactorEvent
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.store.LocalGraphStore
import java.time.Instant
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean


class ModelTaskRepository(
    private val graphStore: LocalGraphStore,
    private val graphCreator: GraphCreator
):
    TaskRepository,
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val maxTerminated = 64
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val active = Collections.synchronizedMap(mutableMapOf<TaskId, ActiveHandle>())

    // TODO: is this necessary?
    private val terminated = Collections.synchronizedMap(mutableMapOf<TaskId, TaskModel>())


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {}


    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}


    @Synchronized
    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        when (event) {
            is DeletedDocumentEvent ->
                documentDeleted(event)

            is RenamedDocumentRefactorEvent ->
                documentRenamed(event)

            else -> {}
        }
    }


    private fun documentDeleted(event: DeletedDocumentEvent) {
        val terminatedDeletedModel =
            terminated.values.find { it.taskLocation.documentPath == event.documentPath }
        if (terminatedDeletedModel != null) {
            terminated.remove(terminatedDeletedModel.taskId)
            return
        }

        val activeDeletedModel =
            active.values.find { it.model.taskLocation.documentPath == event.documentPath }
        if (activeDeletedModel != null) {
            activeDeletedModel.requestCancel()
            activeDeletedModel.awaitTerminal()
            terminated.remove(activeDeletedModel.model.taskId)
        }
    }


    private fun documentRenamed(event: RenamedDocumentRefactorEvent) {
        val terminatedRenamedModel =
            terminated.values.find { it.taskLocation.documentPath == event.documentPath }
        if (terminatedRenamedModel != null) {
            terminated.remove(terminatedRenamedModel.taskId)

            val newTaskLocation = terminatedRenamedModel.taskLocation.copy(
                documentPath = event.createdWithNewName.destination)

            terminated[terminatedRenamedModel.taskId] = terminatedRenamedModel.copy(taskLocation = newTaskLocation)
            return
        }

        val activeRenamedModel =
            active.values.find { it.model.taskLocation.documentPath == event.documentPath }
        if (activeRenamedModel != null) {
            val newTaskLocation = activeRenamedModel.model.taskLocation.copy(
                documentPath = event.createdWithNewName.destination)

            activeRenamedModel.model = activeRenamedModel.model.copy(taskLocation = newTaskLocation)
        }
    }


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
            TaskModel(
                taskId,
                taskLocation,
                request,
                TaskState.Running,
                null,
                null
            ))

        active[taskId] = handle

        val run = task.start(request, handle)

        if (run == null) {
            check(handle.model.state != TaskState.Running)
            handle.run = run
        }

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


    fun queryRun(taskId: TaskId): TaskRun? {
        return active[taskId]?.run
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
        var model: TaskModel,
        var run: TaskRun? = null
    ): TaskHandle {
        private var cancelRequested = AtomicBoolean(false)
        private val completeLatch = CountDownLatch(1)

//        private val requestQueue = LinkedTransferQueue<RequestHandle>()


        override fun complete(result: ExecutionResult) {
            model = model.copy(
                partialResult = null,
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


        override fun update(updater: (ExecutionSuccess?) -> ExecutionSuccess) {
            val nextPartialResult = updater(model.partialResult)

            model = model.copy(
                partialResult = nextPartialResult)
        }


        override fun cancelRequested(): Boolean {
            return cancelRequested.get()
        }


        private fun terminate() {
            run?.close()

            active.remove(model.taskId)
            addTerminated(model)

            completeLatch.countDown()
            Thread.currentThread().interrupt()
        }


        fun requestCancel() {
            cancelRequested.set(true)
        }


        fun awaitTerminal() {
            try {
                completeLatch.await()
            }
            catch (ignored: InterruptedException) {}
        }
    }
}