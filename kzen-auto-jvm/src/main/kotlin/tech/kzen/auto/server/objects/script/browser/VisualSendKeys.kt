package tech.kzen.auto.server.objects.script.browser

import org.openqa.selenium.OutputType
import tech.kzen.auto.common.objects.document.feature.TargetSpec
import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.imperative.api.ScriptStep
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.server.service.ServerContext
import tech.kzen.auto.server.service.vision.VisionUtils
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class VisualSendKeys(
        private val text: String,
        private val target: TargetSpec
): ScriptStep {
    override suspend fun perform(
            imperativeModel: ImperativeModel,
            graphInstance: GraphInstance
    ): ExecutionResult {
        val driver = ServerContext.webDriverContext.get()

        val match = VisionUtils.locateElement(
                target,
                driver,
                ServerContext.notationMedia)

        if (match.isError()) {
            return ExecutionFailure(match.error!!)
        }

        val element = match.webElement!!

        element.sendKeys(text)

        val screenshotPng = driver.getScreenshotAs(OutputType.BYTES)
        return ExecutionSuccess(
                NullExecutionValue,
                BinaryExecutionValue(screenshotPng))
    }

}