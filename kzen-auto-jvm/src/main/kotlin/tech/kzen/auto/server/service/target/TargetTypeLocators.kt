package tech.kzen.auto.server.service.target

import org.openqa.selenium.By
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.common.objects.document.target.FocusTarget
import tech.kzen.auto.common.objects.document.target.TargetSpec
import tech.kzen.auto.common.objects.document.target.TextTarget
import tech.kzen.auto.common.objects.document.target.VisualTarget
import tech.kzen.auto.common.objects.document.target.XpathTarget
import tech.kzen.auto.server.service.vision.VisionUtils


//---------------------------------------------------------------------------------------------------------------------
/** The currently focused element — inherently single, so policy does not apply. */
object FocusTargetLocator: TargetTypeLocator {
    override fun canLocate(spec: TargetSpec): Boolean {
        return spec is FocusTarget
    }


    override suspend fun locate(
        spec: TargetSpec,
        driver: RemoteWebDriver,
        context: TargetLocator
    ): TargetLocator.Result {
        return TargetLocator.Result(driver.switchTo().activeElement(), null)
    }
}


//---------------------------------------------------------------------------------------------------------------------
object TextTargetLocator: TargetTypeLocator {
    override fun canLocate(spec: TargetSpec): Boolean {
        return spec is TextTarget
    }


    override suspend fun locate(
        spec: TargetSpec,
        driver: RemoteWebDriver,
        context: TargetLocator
    ): TargetLocator.Result {
        spec as TextTarget
        val xpathEscaped = VisionUtils.xpathEscape(spec.text)

        // https://stackoverflow.com/a/49906870/1941359
        // https://stackoverflow.com/a/3655588/1941359
        val containingText = driver.findElements(
            By.xpath("//*[text()[contains(.,$xpathEscaped)]]"))

        val candidates = containingText.ifEmpty {
            // e.g. buttons
            driver.findElements(
                By.xpath("//input[contains(@value,$xpathEscaped)]"))
        }

        return when (val selection = TargetLocator.selectByPolicy(candidates, spec.policy)) {
            is TargetLocator.PolicySelection.Selected ->
                TargetLocator.Result(selection.candidate, null)

            is TargetLocator.PolicySelection.Rejected ->
                TargetLocator.Result(null,
                    "${selection.reason}: containing text \"${spec.text}\"")
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
object XpathTargetLocator: TargetTypeLocator {
    override fun canLocate(spec: TargetSpec): Boolean {
        return spec is XpathTarget
    }


    override suspend fun locate(
        spec: TargetSpec,
        driver: RemoteWebDriver,
        context: TargetLocator
    ): TargetLocator.Result {
        spec as XpathTarget
        val candidates = driver.findElements(By.xpath(spec.xpath))

        return when (val selection = TargetLocator.selectByPolicy(candidates, spec.policy)) {
            is TargetLocator.PolicySelection.Selected ->
                TargetLocator.Result(selection.candidate, null)

            is TargetLocator.PolicySelection.Rejected ->
                TargetLocator.Result(null,
                    "${selection.reason}: matching XPath ${spec.xpath}")
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
/** Template-matches the referenced Target document's crops — the shared machinery (tolerance,
 *  preview exclusion, per-crop diagnostics) lives on the [TargetLocator] service. */
object VisualTargetLocator: TargetTypeLocator {
    override fun canLocate(spec: TargetSpec): Boolean {
        return spec is VisualTarget
    }


    override suspend fun locate(
        spec: TargetSpec,
        driver: RemoteWebDriver,
        context: TargetLocator
    ): TargetLocator.Result {
        spec as VisualTarget
        return context.locateElement(spec.document, driver, spec.policy)
    }
}
