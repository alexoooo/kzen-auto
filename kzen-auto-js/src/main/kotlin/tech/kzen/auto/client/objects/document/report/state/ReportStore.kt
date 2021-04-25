package tech.kzen.auto.client.objects.document.report.state

import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash


class ReportStore: SessionGlobal.Observer, ReportDispatcher
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val debounceMillis = 1_500
//        const val debounceMillis = 2_500
//        const val debounceMillis = 5_000
    }

    //-----------------------------------------------------------------------------------------------------------------
    interface Subscriber {
        fun onReportState(reportState: ReportState?)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var subscriber: Subscriber? = null
    private var mounted = false
    private var state: ReportState? = null

    private var refreshAction: ReportAction? = null

    private val refreshDebounce: FunctionWithDebounce = lodash.debounce({
        refreshAction?.let {
            dispatchAsync(it)
        }
    }, debounceMillis)


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


    private fun scheduleRefresh(action: ReportAction) {
        refreshAction = action
        refreshDebounce.apply()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: SessionState) {
//        println("&^%&^&^% onClientState")
        if (! mounted) {
            return
        }

        val nextState = when {
            state == null || ReportState.tryMainLocation(clientState) != state!!.mainLocation -> {
                ReportState.tryCreate(clientState)
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
            subscriber?.onReportState(state)
        }

        if (initial) {
            clearRefresh()
            dispatchAsync(InitiateReport)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun dispatch(action: ReportAction): List<SingularReportAction> {
        val transitiveActions = action
            .flatten()
            .flatMap { dispatchSingular(it) }

//        console.log("ProcessStore - allEffects: " +
//                "${action::class.simpleName} - ${transitiveActions.map { it::class.simpleName }}")

        val refreshSchedule = transitiveActions
            .filterIsInstance<ReportRefreshAction>()
            .lastOrNull()

        if (refreshSchedule != null) {
//            console.log("ProcessStore - refreshSchedule: $refreshSchedule")

            when (refreshSchedule) {
                is ReportRefreshSchedule ->
                    scheduleRefresh(refreshSchedule.refreshAction)

                ReportRefreshCancel ->
                    clearRefresh()
            }
        }

        return transitiveActions
    }


    private suspend fun dispatchSingular(action: SingularReportAction): List<SingularReportAction> {
        val prevState = state
            ?: return listOf()

        val nextState = ReportReducer.reduce(prevState, action)

        if (nextState != prevState) {
            state = nextState
            subscriber?.onReportState(state)
        }

        val effectAction = ReportEffect.effect(nextState, /*prevState,*/ action)
            ?: return listOf(action)

        val transitiveActions = effectAction
            .flatten()
            .flatMap { dispatchSingular(it) }

//        console.log("ProcessStore processSingular: " +
//                "${action::class.simpleName} - ${transitiveActions.map { it::class.simpleName }}")

        return listOf(action) + transitiveActions
    }


    override fun dispatchAsync(action: ReportAction) {
        async {
            dispatch(action)
        }
    }
}