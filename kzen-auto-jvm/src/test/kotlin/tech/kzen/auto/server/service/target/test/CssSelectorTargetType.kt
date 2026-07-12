package tech.kzen.auto.server.service.target.test

import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.common.objects.document.target.*
import tech.kzen.auto.server.service.target.TargetLocator
import tech.kzen.auto.server.service.target.TargetTypeLocator
import tech.kzen.lib.common.model.definition.AttributeDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.lib.common.reflect.ReflectionRegistry
import java.lang.reflect.Proxy


//---------------------------------------------------------------------------------------------------------------------
/**
 * The third-party proof for the open target-type set (target-improvements plan phase 6): a
 * target type defined ENTIRELY in the test source set — spec, notation handler, and locator —
 * registered through the same seams a third-party module would use (an `is: TargetSpecType`
 * notation object + [TargetLocator.register]), with **no edit to TargetSpecDefiner /
 * TargetSpecCreator / TargetLocator dispatch**. TargetExtensibilityTest asserts it defines,
 * creates, and locates end-to-end.
 */
data class CssSelectorTarget(
    val css: String,
    override val policy: TargetMatchPolicy
): TargetSpec


//---------------------------------------------------------------------------------------------------------------------
class CssSelectorTargetSpecType: TargetSpecType() {
    override val typeName = "CssSelector"


    override fun createSpec(
        valueDefinition: AttributeDefinition?,
        policy: TargetMatchPolicy,
        objectLocation: ObjectLocation,
        partialGraphInstance: GraphInstance
    ): TargetSpec {
        val css = (valueDefinition as ValueAttributeDefinition).value as String
        return CssSelectorTarget(css, policy)
    }
}


//---------------------------------------------------------------------------------------------------------------------
object CssSelectorTargetLocator: TargetTypeLocator {
    // A real implementation would driver.findElements(By.cssSelector(spec.css)) and resolve via
    // TargetLocator.selectByPolicy; the stub element proves the dispatch and wiring
    private val stubElement = Proxy.newProxyInstance(
        WebElement::class.java.classLoader,
        arrayOf(WebElement::class.java)
    ) { _, _, _ -> null } as WebElement


    override fun canLocate(spec: TargetSpec): Boolean {
        return spec is CssSelectorTarget
    }


    override suspend fun locate(
        spec: TargetSpec,
        driver: RemoteWebDriver,
        context: TargetLocator
    ): TargetLocator.Result {
        spec as CssSelectorTarget
        return TargetLocator.Result(stubElement, null, "Matched CSS selector ${spec.css}")
    }
}


//---------------------------------------------------------------------------------------------------------------------
/** A minimal graph object with a `target:` attribute, so the definer/creator dispatch runs
 *  through the real notation machinery without needing a browser step's services. */
class TargetSpecHolder(
    val target: TargetSpec
)


//---------------------------------------------------------------------------------------------------------------------
/** Hand-written reflection registration (the test source set has no KSP pass) — the test-source
 *  equivalent of the module registration a third-party would ship; see ScriptStepTestModule. */
object TargetTestModule: ModuleReflection {
    override fun register(reflectionRegistry: ReflectionRegistry) {
        reflectionRegistry.put(
            "tech.kzen.auto.server.service.target.test.CssSelectorTargetSpecType",
            listOf()
        ) { _ ->
            CssSelectorTargetSpecType()
        }

        reflectionRegistry.put(
            "tech.kzen.auto.server.service.target.test.TargetSpecHolder",
            listOf("target")
        ) { args ->
            TargetSpecHolder(args[0] as TargetSpec)
        }
    }
}
