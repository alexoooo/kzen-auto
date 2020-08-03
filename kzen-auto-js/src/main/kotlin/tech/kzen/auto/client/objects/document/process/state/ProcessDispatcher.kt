package tech.kzen.auto.client.objects.document.process.state


interface ProcessDispatcher {
    /**
     * @return effects of given action
     */
    suspend fun dispatch(action: ProcessAction): List<ProcessAction>

    fun dispatchAsync(action: ProcessAction)
}