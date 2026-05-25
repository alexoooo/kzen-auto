package tech.kzen.auto.server.objects.script.api

import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.service.v1.model.LogicResult

/**
 * NB: new instance created every step when paused, use StatefulLogicElement to maintain state
 */
interface ScriptStep {
    fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition?

    fun continueOrStart(scriptExecutionContext: ScriptExecutionContext): LogicResult
}