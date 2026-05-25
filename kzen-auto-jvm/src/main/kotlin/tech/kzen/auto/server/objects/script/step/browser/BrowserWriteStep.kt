package tech.kzen.auto.server.objects.script.step.browser

import org.openqa.selenium.OutputType
import tech.kzen.auto.common.objects.document.feature.TargetSpec
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.TracingScriptStep
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.objects.script.model.ScriptExecutionContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultFailed
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.auto.server.service.vision.VisionUtils
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class BrowserWriteStep(
    private val text: String,
    private val target: TargetSpec,
    selfLocation: ObjectLocation
):
    TracingScriptStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }


    override fun continueOrStart(
        scriptExecutionContext: ScriptExecutionContext
    ): LogicResult {
        val driver = KzenAutoContext.global().webDriverContext.get()

        val match = VisionUtils.locateElement(
            target,
            driver,
            KzenAutoContext.global().notationMedia)

        match.error?.let {
            return LogicResultFailed(it)
        }

        val element = match.webElement!!

        element.sendKeys(text)

        val screenshotPng = driver.getScreenshotAs(OutputType.BYTES)
        traceDetail(scriptExecutionContext, BinaryExecutionValue(screenshotPng))

        return LogicResultSuccess(TupleValue.empty)
    }
}