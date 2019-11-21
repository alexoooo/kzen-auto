package tech.kzen.auto.server.objects.script.browser

//import org.openqa.selenium.By
//import org.openqa.selenium.OutputType
//import org.openqa.selenium.WebElement
//import tech.kzen.auto.common.paradigm.common.model.BinaryExecutionValue
//import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
//import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
//import tech.kzen.auto.common.paradigm.common.model.NullExecutionValue
//import tech.kzen.auto.common.paradigm.imperative.api.ExecutionAction
//import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
//import tech.kzen.auto.server.service.ServerContext
//
//
//@Suppress("unused")
//class SendKeys(
//        private val xpath: String,
//        private val text: String
//): ExecutionAction {
//    override suspend fun perform(
//            imperativeModel: ImperativeModel
//    ): ExecutionResult {
//        // https://stackoverflow.com/questions/44455269/gmail-login-using-selenium-webdriver-in-java
//
//        val driver = ServerContext.webDriverContext.get()
//
//        val element: WebElement =
//                driver.findElement(By.xpath(xpath))
//
//        element.sendKeys(text)
//
//        val screenshotPng = driver.getScreenshotAs(OutputType.BYTES)
//        return ExecutionSuccess(
//                NullExecutionValue,
//                BinaryExecutionValue(screenshotPng))
//    }
//}