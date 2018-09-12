package tech.kzen.auto.common.service


interface ActionExecutor {
    suspend fun execute(actionName: String)
}