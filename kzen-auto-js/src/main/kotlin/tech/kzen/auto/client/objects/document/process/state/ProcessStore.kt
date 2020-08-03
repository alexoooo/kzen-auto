package tech.kzen.auto.client.objects.document.process.state

import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async


class ProcessStore: SessionGlobal.Observer, ProcessDispatcher
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Subscriber {
        fun onProcessState(processState: ProcessState?)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var subscriber: Subscriber? = null
    private var mounted = false
    private var state: ProcessState? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun didMount(subscriber: Subscriber) {
        this.subscriber = subscriber
        mounted = true

        async {
            ClientContext.sessionGlobal.observe(this)
        }
    }


    fun willUnmount() {
        subscriber = null
        mounted = false

        ClientContext.sessionGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: SessionState) {
        if (! mounted) {
            return
        }

        val nextState = when {
            state == null -> {
                ProcessState.tryCreate(clientState)
            }

            ProcessState.tryMainLocation(clientState) != state!!.mainLocation -> {
                null
            }

            else -> {
                state!!.copy(clientState = clientState)
            }
        }

        val initial =
            state == null && nextState != null

        if (state != nextState) {
            state = nextState
            subscriber?.onProcessState(state)
        }

        if (initial) {
            dispatchAsync(InitiateProcessEffect)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun dispatch(action: ProcessAction): List<ProcessAction> {
        val prevState = state
            ?: return listOf()

        val nextState = ProcessReducer.reduce(prevState, action)

        if (nextState != prevState) {
            state = nextState
            subscriber?.onProcessState(state)
        }

        val outcomeAction = ProcessEffect.effect(nextState, prevState, action)
            ?: return listOf()

        val additionalEffects = dispatch(outcomeAction)

        return listOf(outcomeAction) + additionalEffects
    }


    override fun dispatchAsync(action: ProcessAction) {
        async {
            dispatch(action)
        }
    }
}