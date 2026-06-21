package tech.kzen.auto.server.service.exec

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.task.ManagedTask
import tech.kzen.lib.common.exec.task.TaskHandle
import tech.kzen.lib.common.exec.task.TaskRepository
import tech.kzen.lib.common.exec.task.TaskRun
import tech.kzen.lib.common.exec.task.model.TaskId
import tech.kzen.lib.common.exec.task.model.TaskModel
import tech.kzen.lib.common.exec.task.model.TaskState
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.cqrs.DeletedDocumentEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedDocumentRefactorEvent
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.common.service.store.LocalGraphStore
import java.time.Instant
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference


class ModelTaskRepository(
    private val graphStore: LocalGraphStore,
    private val graphCreator: GraphCreator,
    private val environment: () -> GraphEnvironment
):
    TaskRepository,
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val maxTerminated = 64

        private val logger = LoggerFactory.getLogger(ModelTaskRepository::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val active = Collections.synchronizedMap(mutableMapOf<TaskId, ActiveHandle>())
    private val terminated = Collections.synchronizedMap(mutableMapOf<TaskId, TaskModel>())


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {}


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        onCommandSuccessSynchronized(event)
    }


    @Synchronized
    private fun onCommandSuccessSynchronized(event: NotationEvent) {
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
            active.values.find { it.model().taskLocation.documentPath == event.documentPath }
        if (activeDeletedModel != null) {
            activeDeletedModel.markCancelRequested()
            activeDeletedModel.awaitTerminal()
            terminated.remove(activeDeletedModel.model().taskId)
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
            active.values.find { it.model().taskLocation.documentPath == event.documentPath }
        if (activeRenamedModel != null) {
            val newTaskLocation = activeRenamedModel.model().taskLocation.copy(
                documentPath = event.createdWithNewName.destination)

            activeRenamedModel.updateModel { model ->
                model.copy(taskLocation = newTaskLocation)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun submit(taskLocation: ObjectLocation, request: ExecutionRequest): TaskModel {
        val graphDefinition = graphStore
            .graphDefinition()
            .transitiveSuccessful
            .filterDefinitions(AutoConventions.serverAllowed)

        // TODO: add GraphInstanceAttempt for error reporting
        val graphInstance =
            graphCreator.createGraph(graphDefinition, environment())

        val instance = graphInstance.objectInstances[taskLocation]?.reference
            ?: throw IllegalArgumentException("Not found: $taskLocation")

        val task = instance as ManagedTask

        val taskId = TaskId(Instant.now().toString())

        val handle = ActiveHandle(
            AtomicReference(TaskModel(
                taskId,
                taskLocation,
                request,
                TaskState.Running,
                null,
                null
            )))

        active[taskId] = handle

        val run = task.start(request, handle)

        if (run == null) {
            check(handle.model().state != TaskState.Running)
        }
        else {
            handle.run = run
        }

        return handle.model()
    }


    override suspend fun cancel(taskId: TaskId): TaskModel? {
        val handle = active[taskId]
            ?: return terminated[taskId]

        handle.markCancelRequested()

        return handle.model()
    }


    override suspend fun lookupActive(taskLocation: ObjectLocation): Set<TaskId> {
        return active
            .values
            .map { it.model() }
            .filter { it.taskLocation == taskLocation }
            .map { it.taskId }
            .toSet()
    }


    override suspend fun query(taskId: TaskId): TaskModel? {
        return active[taskId]?.model()
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
        val model: AtomicReference<TaskModel>,

        @Volatile
        var run: TaskRun? = null
    ): TaskHandle {
        private var cancelRequested = AtomicBoolean(false)
        private var errorReported = AtomicBoolean(false)
        private val completeLatch = CountDownLatch(1)


        fun model(): TaskModel {
            return model.get()
        }


        fun updateModel(updater: (TaskModel) -> TaskModel) {
            while (true) {
                val value = model.get()
                val updated = updater(value)
                val success = model.compareAndSet(value, updated)

                if (success) {
                    break
                }
            }
        }


        fun updateModelNonTerminal(updater: (TaskModel) -> TaskModel) {
            while (!errorReported.get() && !isTerminated()) {
                val value = model.get()
                val updated = updater(value)
                val success = model.compareAndSet(value, updated)

                if (success) {
                    break
                }
            }
        }


        override fun complete(result: ExecutionResult?) {
            complete(TaskState.FinishedOrFailed, result)
        }

        override fun completeAsCancelled(result: ExecutionResult?) {
            complete(TaskState.Cancelled, result)
        }

        private fun complete(state: TaskState, result: ExecutionResult?) {
            if (isTerminated()) {
                return
            }
            if (isFailed()) {
                terminate()
                return
            }

            updateModel { snapshot ->
                snapshot.copy(
                    partialResult = null,
                    finalResult = result ?: snapshot.partialResult,
                    state = state)
            }

            terminate()
        }


        override fun update(partialResult: ExecutionSuccess) {
            updateModelNonTerminal { snapshot ->
                snapshot.copy(
                    partialResult = partialResult)
            }
        }


        override fun update(updater: (ExecutionSuccess?) -> ExecutionSuccess) {
            updateModelNonTerminal { snapshot ->
                val nextPartialResult = updater(snapshot.partialResult)
                snapshot.copy(
                    partialResult = nextPartialResult)
            }
        }


        override fun terminalFailure(error: ExecutionFailure) {
            val previouslyReported = errorReported.getAndSet(true)
            if (previouslyReported) {
                return
            }

            updateModel { snapshot ->
                snapshot.copy(
                    partialResult = null,
                    finalResult = error,
                    state = TaskState.FinishedOrFailed)
            }
        }


        override fun isFailed(): Boolean {
            return errorReported.get()
        }


        override fun stopRequested(): Boolean {
            return cancelRequested.get() || errorReported.get()
        }


        override fun isTerminated(): Boolean {
            return completeLatch.count == 0L
        }


        private fun terminate() {
            check(!isTerminated()) {
                "Already terminated"
            }

            run?.close(errorReported.get())

            val snapshot = model()
            addTerminated(snapshot)
            active.remove(snapshot.taskId)

            completeLatch.countDown()
        }


        fun markCancelRequested() {
            cancelRequested.set(true)
            updateModel { snapshot ->
                if (snapshot.state == TaskState.Running) {
                    snapshot.copy(state = TaskState.CancelRequested)
                }
                else {
                    snapshot
                }
            }
        }


        override fun awaitTerminal() {
            try {
                completeLatch.await()
            }
            catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.warn("Interrupted while awaiting terminal state for task {}", model().taskId, e)
            }
        }
    }
}