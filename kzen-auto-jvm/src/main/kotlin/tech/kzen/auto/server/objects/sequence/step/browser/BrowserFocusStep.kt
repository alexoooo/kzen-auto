package tech.kzen.auto.server.objects.sequence.step.browser

import org.openqa.selenium.Keys
import org.openqa.selenium.OutputType
import tech.kzen.auto.common.objects.document.feature.TargetSpec
import tech.kzen.auto.common.paradigm.common.model.BinaryExecutionValue
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.sequence.api.TracingSequenceStep
import tech.kzen.auto.server.objects.sequence.model.StepContext
import tech.kzen.auto.server.service.v1.model.LogicResult
import tech.kzen.auto.server.service.v1.model.LogicResultFailed
import tech.kzen.auto.server.service.v1.model.LogicResultSuccess
import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition
import tech.kzen.auto.server.service.v1.model.tuple.TupleValue
import tech.kzen.auto.server.service.vision.VisionUtils
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class BrowserFocusStep(
    private val target: TargetSpec,
    selfLocation: ObjectLocation
):
    TracingSequenceStep(selfLocation)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun valueDefinition(): TupleDefinition {
        return TupleDefinition.empty
    }


    override fun continueOrStart(
        stepContext: StepContext
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

        // https://stackoverflow.com/questions/11337353/correct-way-to-focus-an-element-in-selenium-webdriver-using-java
        element.sendKeys(Keys.SHIFT)
        driver.executeScript("element.focus();")

        val screenshotPng = driver.getScreenshotAs(OutputType.BYTES)
        traceDetail(stepContext, BinaryExecutionValue(screenshotPng))

        return LogicResultSuccess(TupleValue.empty)
    }
}