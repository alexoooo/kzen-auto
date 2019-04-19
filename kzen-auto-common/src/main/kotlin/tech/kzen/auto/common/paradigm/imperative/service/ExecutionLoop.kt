package tech.kzen.auto.common.paradigm.imperative.service

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import tech.kzen.auto.common.paradigm.imperative.ImerativeControlFlow
import tech.kzen.auto.common.paradigm.imperative.model.ExecutionModel
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation


class ExecutionLoop(
        private val modelManager: ModelManager,
        private val executionManager: ExecutionManager,
//        private val delayMillis: Int = 1000
        private val delayMillis: Int = 0
):
        ExecutionManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    private val states = mutableMapOf<DocumentPath, State>()

    private class State {
        var running: Boolean = false
        var executionModel: ExecutionModel? = null
    }


    private fun getOrCreate(host: DocumentPath): State {
        return states.getOrPut(host) { State() }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {}


    override suspend fun onExecutionModel(
            host: DocumentPath,
            executionModel: ExecutionModel
    ) {
        val state = getOrCreate(host)

        state.executionModel = executionModel

        if (! state.running) {
            return
        }

        val next = ImerativeControlFlow.next(
                modelManager.graphStructure().graphNotation,
                executionModel
        ) ?: return

//        println("$$$$ onExecutionModel - $host - $next - $executionModel")

        run(host, next)
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun running() = running


    suspend fun run(host: DocumentPath) {
        val state = getOrCreate(host)
//        println("ExecutionLoop | Run request")

        if (state.running) {
//            println("ExecutionLoop | Already running")
            return
        }
        state.running = true

//        println("ExecutionLoop | executionModel is $executionModel")

        val next = state.executionModel?.let {
            ImerativeControlFlow.next(modelManager.graphStructure().graphNotation, it)
        }
        if (next == null) {
//            println("ExecutionLoop | pausing at end of loop")

            // NB: auto-pause
            state.running = false
            return
        }

//        println("ExecutionLoop |^ next is $next")

        run(host, next)
    }


    private suspend fun run(
            host: DocumentPath,
            next: ObjectLocation
    ) {
        // NB: break cycle, is there a cleaner way to do this?
        GlobalScope.async {
            // NB: this will trigger ExecutionManager.Observer onExecutionModel method above
            executionManager.execute(host, next, delayMillis)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun running(host: DocumentPath): Boolean {
        return getOrCreate(host).running
    }


    fun pause(host: DocumentPath) {
        val state = getOrCreate(host)
//        println("ExecutionLoop | Pause request")

        if (! state.running) {
//            println("ExecutionLoop | Already paused")
            return
        }

//        println("ExecutionLoop | Pausing")

        state.running = false
    }
}