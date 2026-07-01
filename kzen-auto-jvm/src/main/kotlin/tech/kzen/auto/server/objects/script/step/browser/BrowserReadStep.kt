package tech.kzen.auto.server.objects.script.step.browser

import org.openqa.selenium.OutputType
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.common.objects.document.feature.TargetSpec
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.api.StepExecution
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.service.vision.VisionUtils
import tech.kzen.auto.server.service.webdriver.WebDriverSupport
import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.media.NotationMedia


@Reflect
class BrowserReadStep(
    private val target: TargetSpec,
    private val attribute: String,
    @Suppress("unused") selfLocation: ObjectLocation,
    @Service private val notationMedia: NotationMedia
):
    ScriptStep
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType.string))
    }


    override suspend fun run(execution: StepExecution): Any? {
        val driver = execution.resource(WebDriverSupport.resourceKey) as? RemoteWebDriver
            ?: error("Browser is not open")

        val match = VisionUtils.locateElement(target, driver, notationMedia)
        match.error?.let {
            error(it)
        }

        val element = match.webElement!!

        // Default: the element's visible text. With `attribute` set, read that DOM attribute instead —
        // needed for signals that live in an attribute rather than text (e.g. an icon button's @title).
        val text =
            if (attribute.isBlank()) {
                element.text.trim()
            }
            else {
                (element.getDomAttribute(attribute) ?: "").trim()
            }

        // Screenshot the page as the step's detail (like the other browser steps), so a read shows what
        // was on screen when the value was captured — and feeds the RunStep detail film strip.
        val screenshotPng = driver.getScreenshotAs(OutputType.BYTES)
        execution.traceDetail(BinaryExecutionValue(screenshotPng))

        return text
    }
}
