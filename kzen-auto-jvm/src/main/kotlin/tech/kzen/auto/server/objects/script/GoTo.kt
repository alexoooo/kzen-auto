package tech.kzen.auto.server.objects.script

import org.openqa.selenium.OutputType
import tech.kzen.auto.common.paradigm.common.model.BinaryExecutionValue
import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeResult
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import tech.kzen.auto.server.service.ServerContext


@Suppress("unused")
class GoTo(
        private val location: String
): ExecutionAction {
    override suspend fun perform(
            imperativeModel: ImperativeModel
    ): ImperativeResult {
        val driver = ServerContext.webDriverContext.get()

        driver.get(location)

        val screenshotPng = driver.getScreenshotAs(OutputType.BYTES)
        return ImperativeSuccess(
                NullExecutionValue,
                BinaryExecutionValue(screenshotPng))
    }
}