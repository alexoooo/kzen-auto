package tech.kzen.auto.server.objects.script.step.browser

import org.openqa.selenium.Keys
import org.openqa.selenium.Platform
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.common.objects.document.target.TargetSpec
import tech.kzen.auto.server.service.target.TargetLocator
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


@Reflect
class BrowserWriteStep(
    private val text: String,
    target: TargetSpec,
    private val overwrite: Boolean,
    @Suppress("unused") selfLocation: ObjectLocation,
    @Service targetLocator: TargetLocator
):
    BrowserTargetStep(target, targetLocator)
{
    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun act(element: WebElement, driver: RemoteWebDriver): Any? {
        if (overwrite) {
            // select existing content so the typed text replaces it
            // (element.clear() is a no-op on contenteditable / custom inputs).
            // macOS selects-all with Command, every other platform with Control
            val selectAllModifier =
                if (driver.capabilities.platformName?.`is`(Platform.MAC) == true) {
                    Keys.COMMAND
                }
                else {
                    Keys.CONTROL
                }
            element.sendKeys(Keys.chord(selectAllModifier, "a"))
        }

        // Write the text one line at a time, pressing Enter between lines, so a multi-line
        // `text` is written as multiple lines (e.g. into a textarea) rather than relying on the
        // driver to map a raw newline character. String.lines() normalises \r\n, \r and \n breaks.
        val lines = text.lines()
        val keystrokes = buildList<CharSequence> {
            lines.forEachIndexed { index, line ->
                if (index != 0) {
                    add(Keys.ENTER)
                }
                add(line)
            }
        }
        element.sendKeys(*keystrokes.toTypedArray())

        return null
    }
}
