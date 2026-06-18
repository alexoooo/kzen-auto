package tech.kzen.auto.server.objects.script.step.browser

import tech.kzen.auto.common.objects.document.feature.TargetSpec
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.service.vision.VisionUtils
import tech.kzen.auto.server.service.webdriver.WebDriverContext
import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.logic.model.LogicResultFailed
import tech.kzen.lib.common.exec.logic.model.LogicResultSuccess
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.media.NotationMedia


@Reflect
class BrowserReadStep(
    private val target: TargetSpec,
    selfLocation: ObjectLocation,
    @Service private val webDriverContext: WebDriverContext,
    @Service private val notationMedia: NotationMedia
):
    TracingScriptStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType.string))
    }


    override fun continueOrStart(
        scriptExecutionContext: ScriptExecutionContext
    ): LogicResult {
        val driver = webDriverContext.get()

        val match = VisionUtils.locateElement(
            target,
            driver,
            notationMedia)

        match.error?.let {
            return LogicResultFailed(it)
        }

        val text = match.webElement!!.text.trim()

        traceValue(scriptExecutionContext, text)

        return LogicResultSuccess(
            TupleValue.ofMain(text))
    }
}
