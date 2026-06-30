package tech.kzen.auto.test.server.script.step

import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.server.objects.script.api.ScriptStep
import tech.kzen.auto.server.objects.script.api.ScriptStepDefinition
import tech.kzen.auto.server.objects.script.model.ScriptDefinitionContext
import tech.kzen.lib.common.exec.logic.ResourceClosePolicy
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


@Reflect
class StartKzenAutoStep(
    @Suppress("unused") private val name: String,
    @Suppress("unused") private val fixture: String,
    @Suppress("unused") private val port: Int,
    @Suppress("unused") private val closePolicy: ResourceClosePolicy,
    @Suppress("unused") selfLocation: ObjectLocation,
    @Suppress("unused") @Service private val config: KzenAutoConfig
):
    ScriptStep
{
    override fun definition(scriptDefinitionContext: ScriptDefinitionContext): ScriptStepDefinition {
        return ScriptStepDefinition.empty
    }
}
