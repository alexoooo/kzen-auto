package tech.kzen.auto.common.service

import tech.kzen.auto.common.exec.ExecutionModel


class ExecutionLoop(
        private val executionManager: ExecutionManager,
//        private val delayMillis: Int = 1000
        private val delayMillis: Int = 0
) : ExecutionManager.Subscriber {
    //-----------------------------------------------------------------------------------------------------------------
    private var running: Boolean = false
    private var executionModel: ExecutionModel? = null


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeExecution(executionModel: ExecutionModel) {}


    override suspend fun afterExecution(executionModel: ExecutionModel) {
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
        println("ExecutionLoop | Run request")

        if (running) {
            println("ExecutionLoop | Already running")
            return
        }
        running = true

        println("ExecutionLoop | executionModel is $executionModel")

        val next = executionModel?.next()
                ?: return

        println("ExecutionLoop | next is $next")

        run(next)
    }


    private suspend fun run(
            next: String
    ) {
        // NB: this will trigger ExecutionManager.Subscriber afterExecution method above
        executionManager.execute(next, delayMillis)
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun running() = running
    fun pause() {
        println("ExecutionLoop | Pause request")

        if (! running) {
            println("ExecutionLoop | Already paused")
            return
        }

        println("ExecutionLoop | Pausing")

        running = false
    }
}