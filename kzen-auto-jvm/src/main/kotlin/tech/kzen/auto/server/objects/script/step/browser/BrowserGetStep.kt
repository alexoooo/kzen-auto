package tech.kzen.auto.server.objects.script.step.browser

import kotlinx.coroutines.delay
import org.openqa.selenium.OutputType
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.api.context
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class BrowserGetStep(
    private val location: String,
    private val screenshotDelayMilliseconds: Long,
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        val driver = execution.context<RemoteWebDriver>()

        execution.blocking { driver.get(location) }

        if (screenshotDelayMilliseconds > 0) {
            delay(screenshotDelayMilliseconds)
        }

        val screenshotPng = execution.blocking { driver.getScreenshotAs(OutputType.BYTES) }
        execution.traceDetail(BinaryExecutionValue(screenshotPng))

        return null
    }
}
