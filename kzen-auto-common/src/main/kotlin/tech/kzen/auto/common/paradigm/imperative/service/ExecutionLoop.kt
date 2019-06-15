package tech.kzen.auto.common.paradigm.imperative.service

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.util.ImperativeUtils
import tech.kzen.auto.common.service.GraphStructureManager
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation


class ExecutionLoop(
        private val graphStructureManager: GraphStructureManager,
        private val executionManager: ExecutionManager,
        private val delayMillis: Int = 0
):
        ExecutionManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    private val states = mutableMapOf<DocumentPath, State>()

    private class State {
        var looping: Boolean = false
        var executionModel: ImperativeModel? = null
    }


    private fun getOrCreate(host: DocumentPath): State {
        return states.getOrPut(host) { State() }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {}


    override suspend fun onExecutionModel(
            host: DocumentPath,
            executionModel: ImperativeModel
    ) {
        val state = getOrCreate(host)

        state.executionModel = executionModel

        if (! state.looping) {
            return
        }

        val next = ImperativeUtils.next(
                graphStructureManager.serverGraphStructure().graphNotation,
                executionModel
        ) ?: return

//        println("$$$$ onExecutionModel - $host - $next - $executionModel")

        run(host, next)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun run(host: DocumentPath) {
        val state = getOrCreate(host)
//        println("ExecutionLoop | Run request")

        if (state.looping) {
//            println("ExecutionLoop | Already running")
            return
        }
        state.looping = true

//        println("ExecutionLoop | executionModel is $executionModel")

        val next = state.executionModel?.let {
            ImperativeUtils.next(graphStructureManager.serverGraphStructure().graphNotation, it)
        }
        if (next == null) {
//            println("ExecutionLoop | pausing at end of loop")

            // NB: auto-pause
            state.looping = false
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
        @Suppress("DeferredResultUnused")
        GlobalScope.async {
            // NB: this will trigger ExecutionManager.Observer onExecutionModel method above
            executionManager.execute(host, next, delayMillis)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun running(host: DocumentPath): Boolean {
        return getOrCreate(host).looping
    }


    fun pause(host: DocumentPath) {
        val state = getOrCreate(host)
//        println("ExecutionLoop | Pause request - $states")

        if (! state.looping) {
//            println("ExecutionLoop | Already paused")
            return
        }

//        println("ExecutionLoop | Pausing")

        state.looping = false
    }


    fun pauseAll() {
//        println("^^^^^ pauseAll - $states")
        for (state in states) {
            state.value.looping = false
        }
    }
}