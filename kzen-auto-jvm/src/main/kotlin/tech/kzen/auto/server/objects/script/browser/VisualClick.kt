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
class VisualClick(
        private val target: TargetSpec
): ScriptStep {
    override suspend fun perform(
            imperativeModel: ImperativeModel,
            graphInstance: GraphInstance
    ): ExecutionResult {
        val driver = ServerContext.webDriverContext.get()

        val targetLocation = VisionUtils.locateElement(
                target, driver, ServerContext.notationMedia)

        if (targetLocation.isError()) {
            return ExecutionFailure(targetLocation.error!!)
        }

        val element = targetLocation.webElement!!

        if (element.tagName.toLowerCase() == "input" &&
                element.getAttribute("type").toLowerCase() == "submit") {
            element.submit()
        }
        else {
            element.click()
        }

        val postScreenshotPngBytes = driver.getScreenshotAs(OutputType.BYTES)
        return ExecutionSuccess(
                NullExecutionValue,
                BinaryExecutionValue(postScreenshotPngBytes))
    }
}