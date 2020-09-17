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
//        console.log("^^^^ ProcessStore - mount")

        this.subscriber = subscriber
        mounted = true

        async {
            ClientContext.sessionGlobal.observe(this)
        }
    }


    fun willUnmount() {
//        console.log("^^^^ ProcessStore - unmount")

        subscriber = null
        mounted = false
        state = null

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
            state == null || ProcessState.tryMainLocation(clientState) != state!!.mainLocation -> {
                ProcessState.tryCreate(clientState)
            }

            else -> {
                state!!.copy(clientState = clientState)
            }
        }

        val initial =
            state == null && nextState != null ||
            state?.mainLocation != nextState?.mainLocation

        if (state != nextState) {
            state = nextState
            subscriber?.onProcessState(state)
        }

        if (initial) {
            clearRefresh()
            async {
                dispatch(InitiateProcessStart)
                dispatch(InitiateProcessDone)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun dispatch(action: ProcessAction): List<SingularProcessAction> {
        val transitiveActions =  action
            .flatten()
            .flatMap { dispatchSingular(it) }

//        console.log("ProcessStore - allEffects: " +
//                "${action::class.simpleName} - ${transitiveActions.map { it::class.simpleName }}")

        val refreshSchedule = transitiveActions
            .filterIsInstance<ProcessRefreshAction>()
            .lastOrNull()

        if (refreshSchedule != null) {
//            console.log("ProcessStore - refreshSchedule: $refreshSchedule")

            when (refreshSchedule) {
                is ProcessRefreshSchedule ->
                    scheduleRefresh(refreshSchedule.refreshAction)

                ProcessRefreshCancel ->
                    clearRefresh()
            }
        }

        return transitiveActions
    }


    private suspend fun dispatchSingular(action: SingularProcessAction): List<SingularProcessAction> {
        val prevState = state
            ?: return listOf()

        val nextState = ProcessReducer.reduce(prevState, action)

        if (nextState != prevState) {
            state = nextState
            subscriber?.onProcessState(state)
        }

        val effectAction = ProcessEffect.effect(nextState, /*prevState,*/ action)
            ?: return listOf(action)

        val transitiveActions = effectAction
            .flatten()
            .flatMap { dispatchSingular(it) }

//        console.log("ProcessStore processSingular: " +
//                "${action::class.simpleName} - ${transitiveActions.map { it::class.simpleName }}")

        return listOf(action) + transitiveActions
    }


    override fun dispatchAsync(action: ProcessAction) {
        async {
            dispatch(action)
        }
    }
}