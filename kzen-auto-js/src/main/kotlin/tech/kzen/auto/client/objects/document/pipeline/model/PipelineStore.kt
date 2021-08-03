package tech.kzen.auto.client.objects.document.pipeline.model

import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputState
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputStore
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.locate.ObjectLocation


class PipelineStore: SessionGlobal.Observer {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        fun onPipelineState(pipelineState: PipelineState/*, initial: Boolean*/)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var observer: Observer? = null
    private var mounted = false
    private var state: PipelineState? = null


    val input = PipelineInputStore(this)


    //-----------------------------------------------------------------------------------------------------------------
    fun didMount(subscriber: Observer) {
        this.observer = subscriber
        mounted = true

        async {
            ClientContext.sessionGlobal.observe(this)
        }
    }


    fun willUnmount() {
        observer = null
        mounted = false
        state = null

        ClientContext.sessionGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: SessionState) {
        if (! mounted) {
            return
        }

//        println("got client state")
        val pipelineMainLocation = PipelineState.tryMainLocation(clientState)
            ?: return
//        println("pipelineMainLocation: $pipelineMainLocation")

        val pipelineMainDefinition = mainDefinition(clientState, pipelineMainLocation)

        val previousState = state
        val nextState = when {
            previousState == null || pipelineMainLocation != previousState.mainLocation -> {
                PipelineState(
                    pipelineMainLocation,
                    pipelineMainDefinition,
                    PipelineInputState())
            }

            else -> {
                previousState.copy(mainDefinition = pipelineMainDefinition)
            }
        }

        val initial =
            previousState == null ||
            previousState.mainLocation != nextState.mainLocation

        if (state != nextState) {
            state = nextState
            observer?.onPipelineState(nextState/*, initial*/)
        }

        if (initial) {
            initAsync()
        }
    }


    private fun initAsync() {
        input.initAsync()
    }


    private fun mainDefinition(clientState: SessionState, mainLocation: ObjectLocation): ObjectDefinition {
        return clientState
            .graphDefinitionAttempt
            .objectDefinitions[mainLocation]!!
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun state(): PipelineState {
        return state
            ?: throw IllegalStateException("Get state before initialized")
    }


    fun mainLocation(): ObjectLocation {
        return state().mainLocation
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun updateAsync(updater: suspend (PipelineState) -> PipelineState) {
        val initializedState = state
            ?: throw IllegalStateException("Update before initialized")

        val updated = updater(initializedState)

        if (state != updated) {
            state = updated
            observer?.onPipelineState(updated/*, false*/)
        }
    }


    fun update(updater: (PipelineState) -> PipelineState) {
        val initializedState = state
            ?: throw IllegalStateException("Update before initialized")

        val updated = updater(initializedState)

        if (state != updated) {
            state = updated
            observer?.onPipelineState(updated/*, false*/)
        }
    }


    fun update(state: PipelineState) {
        if (this.state != state) {
            this.state = state
            observer?.onPipelineState(state/*, false*/)
        }
    }
}