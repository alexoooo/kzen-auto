package tech.kzen.auto.common.paradigm.imperative.service

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.util.ImperativeUtils
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.service.store.MirroredGraphStore


class ExecutionLoop(
        private val mirroredGraphStore: MirroredGraphStore,
        private val executionRepository: ExecutionRepository,
        private val delayMillis: Int = 0
):
        ExecutionRepository.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    private val states = mutableMapOf<DocumentPath, State>()

    private data class State(
        var looping: Boolean = false,
        var executionModel: ImperativeModel? = null,
        var returningFrame: Boolean = false,
        var continuingFrame: Boolean = false
    )


    private fun getOrCreate(host: DocumentPath): State {
        return states.getOrPut(host) { State() }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun beforeExecution(host: DocumentPath, objectLocation: ObjectLocation) {}


    override suspend fun onExecutionModel(
            host: DocumentPath,
            executionModel: ImperativeModel?
    ) {
        if (executionModel == null) {
            states.remove(host)
            return
        }

        val state = getOrCreate(host)

        state.executionModel = executionModel

        if (! state.looping) {
            return
        }

        nextIteration(state, host, executionModel)
    }


    private suspend fun nextIteration(
            state: State,
            host: DocumentPath,
            executionModel: ImperativeModel
    ) {
        val serverGraphStructure = mirroredGraphStore
                .graphStructure()
                .filter(AutoConventions.serverAllowed)

        val next = ImperativeUtils.next(
                serverGraphStructure,
                executionModel
        ) ?: return

//        println("$$$$ nextIteration - $host - $next - $executionModel")

        if (state.returningFrame) {
            state.returningFrame = false
            state.continuingFrame = true
        }
        else {
            run(host, next, serverGraphStructure)
        }
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

        val serverGraphStructure = mirroredGraphStore
                .graphStructure()
                .filter(AutoConventions.serverAllowed)

        val next = state.executionModel?.let {
            ImperativeUtils.next(serverGraphStructure, it)
        }

        if (next == null) {
//            println("ExecutionLoop | pausing at end of loop")

            // NB: auto-pause
            state.looping = false
            return
        }

//        println("ExecutionLoop |^ next is $next")

        run(host, next, serverGraphStructure)
    }


    private suspend fun run(
            host: DocumentPath,
            next: ObjectLocation,
            graphStructure: GraphStructure
    ) {
        // NB: break cycle, is there a cleaner way to do this?
        @Suppress("DeferredResultUnused")
        GlobalScope.async {
            // NB: this will trigger ExecutionManager.Observer onExecutionModel method above
            executionRepository.execute(host, next, graphStructure, delayMillis)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isLooping(host: DocumentPath): Boolean {
        return getOrCreate(host).looping
    }


    fun isContinuingFrame(host: DocumentPath): Boolean {
        return getOrCreate(host).continuingFrame
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


    fun returnFrame(host: DocumentPath) {
        val state = getOrCreate(host)
        if (! state.looping) {
            return
        }
        state.returningFrame = true
    }


    suspend fun continueFrame(host: DocumentPath) {
        val state = getOrCreate(host)

//        println("^^^^^^^ continueFrame - $state")

        state.continuingFrame = false
        state.looping = true

        val executionModel = state.executionModel
                ?: throw IllegalStateException("missing execution model: $state")

        nextIteration(state, host, executionModel)
    }


    fun pauseAll() {
//        println("^^^^^ pauseAll - $states")
        for (state in states) {
            state.value.looping = false
        }
    }
}