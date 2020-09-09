package tech.kzen.auto.client.objects.document.process.state

import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash


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

    private var refreshAction: ProcessAction? = null

    private val refreshDebounce: FunctionWithDebounce = lodash.debounce({
        refreshAction?.let {
            dispatchAsync(it)
        }
    }, 5_000)


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
    private fun clearRefresh() {
        refreshDebounce.cancel()
        refreshAction = null
    }


    private fun scheduleRefresh(action: ProcessAction) {
        refreshAction = action
        refreshDebounce.apply()
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
            clearRefresh()
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

        val outcomeActions = ProcessEffect.effect(nextState, /*prevState,*/ action)

        val allEffects =
            outcomeActions +
            outcomeActions.flatMap{
                dispatch(it)
            }

        val refreshSchedule = allEffects
            .filterIsInstance<ProcessRefreshSchedule>()
            .lastOrNull()

        if (refreshSchedule != null) {
            scheduleRefresh(refreshSchedule.refreshAction)
        }

        return allEffects
    }


    override fun dispatchAsync(action: ProcessAction) {
        async {
            dispatch(action)
        }
    }
}