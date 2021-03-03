package tech.kzen.auto.plugin.definition


/**
 * Will be instantiated using reflection, must have a no-args constructor
 */
interface ProcessorDefiner<Output> {
    fun info(): ProcessorDefinitionInfo
    fun define(): ProcessorDefinition<Output>
}