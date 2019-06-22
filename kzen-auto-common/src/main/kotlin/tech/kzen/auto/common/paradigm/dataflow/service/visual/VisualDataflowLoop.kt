package tech.kzen.auto.common.paradigm.dataflow.service.visual

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import tech.kzen.auto.common.paradigm.dataflow.model.exec.VisualDataflowModel
import tech.kzen.auto.common.paradigm.dataflow.util.DataflowUtils
import tech.kzen.auto.common.service.GraphStructureManager
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation


class VisualDataflowLoop(
        private val graphStructureManager: GraphStructureManager,
        private val visualDataflowManager: VisualDataflowManager,
        private val delayMillis: Int = 0
):
        VisualDataflowManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    private val states = mutableMapOf<DocumentPath, State>()

    private class State {
        var looping: Boolean = false
        var visualDataflowModel: VisualDataflowModel? = null
    }


    private fun getOrCreate(host: DocumentPath): State {
        return states.getOrPut(host) { State() }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeDataflowExecution(host: DocumentPath, vertexLocation: ObjectLocation) {}

    override suspend fun onVisualDataflowModel(
            host: DocumentPath,
            visualDataflowModel: VisualDataflowModel
    ) {
        val state = getOrCreate(host)

        state.visualDataflowModel = visualDataflowModel

        if (! state.looping || visualDataflowModel.isRunning()) {
            return
        }

        val next = DataflowUtils.next(
                host,
                graphStructureManager.serverGraphStructure(),
                visualDataflowModel
        ) ?: return

//        println("$$$$ onExecutionModel - $host - $next - $visualDataflowModel")

        run(host, next)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun loop(host: DocumentPath) {
        val state = getOrCreate(host)
//        println("ExecutionLoop | Run request")

        if (state.looping) {
//            println("ExecutionLoop | Already running")
            return
        }
        state.looping = true

//        println("ExecutionLoop | executionModel is $executionModel")

        val next = state.visualDataflowModel?.let {
            DataflowUtils.next(
                    host,
                    graphStructureManager.serverGraphStructure(),
                    it)
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
        GlobalScope.async {
            // NB: this will trigger VisualDataflowManager.Observer onExecutionModel method above
            visualDataflowManager.execute(host, next, delayMillis)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isLooping(host: DocumentPath): Boolean {
        return getOrCreate(host).looping
    }


    fun pause(host: DocumentPath) {
        val state = getOrCreate(host)
//        println("ExecutionLoop | Pause request")

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