package tech.kzen.auto.plugin.definition


/**
 * Will be instantiated using reflection, must have a no-args constructor
 */
interface ReportDefiner<Output> {
    fun info(): ReportDefinitionInfo
    fun define(): ReportDefinition<Output>
}