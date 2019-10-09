package tech.kzen.auto.server.objects.script.browser

import org.openqa.selenium.OutputType
import tech.kzen.auto.common.objects.document.feature.FeatureDocument
import tech.kzen.auto.common.paradigm.common.model.BinaryExecutionValue
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import tech.kzen.auto.server.service.ServerContext


class VisualClick(
        private val target: FeatureDocument
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ImperativeResult {
        val driver = ServerContext.webDriverContext.get()

        val resources = target.documentNotation.resources!!
        println("resources: $resources")
//        val element: WebElement =
//                driver.findElement(By.xpath(xpath))
//
//        element.click()

        val screenshotPng = driver.getScreenshotAs(OutputType.BYTES)
        return ImperativeSuccess(
                NullExecutionValue,
                BinaryExecutionValue(screenshotPng))
    }
}