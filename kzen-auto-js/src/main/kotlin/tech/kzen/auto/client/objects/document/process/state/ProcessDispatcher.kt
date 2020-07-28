package tech.kzen.auto.client.objects.document.process.state


interface ProcessDispatcher {
    fun dispatch(action: ProcessAction)
}