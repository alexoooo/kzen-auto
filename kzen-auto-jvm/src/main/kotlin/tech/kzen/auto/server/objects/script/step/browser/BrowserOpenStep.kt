package tech.kzen.auto.server.objects.script.step.browser

import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.service.webdriver.WebDriverContext
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


@Reflect
class BrowserOpenStep(
    @Suppress("unused") private val closePolicy: ResourceClosePolicy,
    @Suppress("unused") selfLocation: ObjectLocation,
    @Service @Suppress("unused") private val webDriverContext: WebDriverContext
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }
}
