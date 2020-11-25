package tech.kzen.auto.client.objects.document.report.state


interface ReportDispatcher {
    /**
     * @return effects of given action
     */
    suspend fun dispatch(action: ReportAction): List<ReportAction>

    fun dispatchAsync(action: ReportAction)
}