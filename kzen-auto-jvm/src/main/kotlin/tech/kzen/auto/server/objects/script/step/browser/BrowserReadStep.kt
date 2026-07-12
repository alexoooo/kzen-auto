package tech.kzen.auto.server.objects.script.step.browser

import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.common.objects.document.target.TargetSpec
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.auto.server.service.target.TargetLocator
import tech.kzen.lib.common.exec.logic.model.LogicType
import tech.kzen.lib.common.exec.tuple.TupleDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


@Reflect
class BrowserReadStep(
    target: TargetSpec,
    private val attribute: String,
    @Suppress("unused") selfLocation: ObjectLocation,
    @Service targetLocator: TargetLocator
):
    BrowserTargetStep(target, targetLocator)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.of(
            TupleDefinition.ofMain(LogicType.string))
    }


    override suspend fun act(element: WebElement, driver: RemoteWebDriver): Any? {
        // Default: the element's visible text. With `attribute` set, read that DOM attribute instead —
        // needed for signals that live in an attribute rather than text (e.g. an icon button's @title).
        return if (attribute.isBlank()) {
            element.text.trim()
        }
        else {
            (element.getDomAttribute(attribute) ?: "").trim()
        }
    }
}
