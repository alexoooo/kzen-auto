package tech.kzen.auto.common.paradigm.imperative.service

import tech.kzen.auto.common.paradigm.imperative.model.ExecutionModel
import tech.kzen.lib.common.model.locate.ObjectLocation


class ExecutionLoop(
        private val executionManager: ExecutionManager,
//        private val delayMillis: Int = 1000
        private val delayMillis: Int = 0
): ExecutionManager.Observer {
    //-----------------------------------------------------------------------------------------------------------------
    private var running: Boolean = false
    private var executionModel: ExecutionModel? = null


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeExecution(objectLocation: ObjectLocation) {}


    override suspend fun onExecutionModel(executionModel: ExecutionModel) {
        this.executionModel = executionModel

        if (! running) {
            return
        }

        val next = executionModel.next()
                ?: return

        run(next)
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun running() = running


    suspend fun run() {
//        println("ExecutionLoop | Run request")

        if (running) {
//            println("ExecutionLoop | Already running")
            return
        }
        running = true

//        println("ExecutionLoop | executionModel is $executionModel")

        val next = executionModel?.next()
        if (next == null) {
//            println("ExecutionLoop | pausing at end of loop")

            // NB: auto-pause
            running = false
            return
        }

//        println("ExecutionLoop |^ next is $next")

        run(next)
    }


    private suspend fun run(
            next: ObjectLocation
    ) {
        // NB: this will trigger ExecutionManager.Observer onExecutionModel method above
        executionManager.execute(next, delayMillis)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun running(): Boolean {
        return running
    }


    fun pause() {
//        println("ExecutionLoop | Pause request")

        if (! running) {
//            println("ExecutionLoop | Already paused")
            return
        }

//        println("ExecutionLoop | Pausing")

        running = false
    }
}