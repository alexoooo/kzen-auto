package tech.kzen.auto.server.objects.script.step.browser

import kotlinx.coroutines.delay
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.service.webdriver.WebDriverSupport
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class BrowserCloseStep(
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override suspend fun run(execution: StepExecution): Any? {
        delay(250)

        // Explicit close: dispose the browser ourselves and deregister it so the engine's auto-disposer won't
        // fire a second time (this is what a Manual closePolicy relies on).
        (execution.resource(WebDriverSupport.resourceKey) as? RemoteWebDriver)?.let {
            execution.blocking { WebDriverSupport.quitQuietly(it) }
        }
        execution.releaseResource(WebDriverSupport.resourceKey)

        execution.traceDetail("Browser closed")

        return null
    }
}
