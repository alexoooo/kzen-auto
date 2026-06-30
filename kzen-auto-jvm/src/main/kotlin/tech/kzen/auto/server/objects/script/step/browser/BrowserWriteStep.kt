package tech.kzen.auto.server.objects.script.step.browser

import tech.kzen.auto.common.objects.document.feature.TargetSpec
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.service.webdriver.WebDriverContext
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.media.NotationMedia


@Reflect
class BrowserWriteStep(
    @Suppress("unused") private val text: String,
    @Suppress("unused") private val target: TargetSpec,
    @Suppress("unused") private val overwrite: Boolean,
    @Suppress("unused") selfLocation: ObjectLocation,
    @Service @Suppress("unused") private val webDriverContext: WebDriverContext,
    @Service @Suppress("unused") private val notationMedia: NotationMedia
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }
}
