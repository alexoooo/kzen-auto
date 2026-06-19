package tech.kzen.auto.client.objects.document.script.step.control

import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.script.command.ScriptCommander
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayProps
import tech.kzen.auto.client.objects.document.script.display.ScriptStepDisplayWrapper
import tech.kzen.auto.client.objects.document.script.display.StepDisplayManager
import tech.kzen.auto.client.objects.document.script.display.dependency.ScriptBranchDisplay
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.normal.ObjectStableMapper


//---------------------------------------------------------------------------------------------------------------------
external interface MultiStepDisplayProps: ScriptStepDisplayProps {
    var stepDisplayManager: StepDisplayManager.Handle
    var scriptCommander: ScriptCommander

    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
    var objectStableMapper: ObjectStableMapper
}


//---------------------------------------------------------------------------------------------------------------------
class MultiStepDisplay(
    props: MultiStepDisplayProps
):
    RPureComponent<MultiStepDisplayProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val stepDisplayManager: StepDisplayManager.Handle,
        private val scriptCommander: ScriptCommander,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val objectStableMapper: ObjectStableMapper
    ):
        ScriptStepDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: ScriptStepDisplayProps.() -> Unit) {
            MultiStepDisplay::class.react {
                stepDisplayManager = this@Wrapper.stepDisplayManager
                scriptCommander = this@Wrapper.scriptCommander
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                objectStableMapper = this@Wrapper.objectStableMapper
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val stepDisplayManager = props.stepDisplayManager.wrapper
            ?: return

        ScriptBranchDisplay::class.react {
            attributeLocation = AttributeLocation(
                props.common.objectLocation,
                ScriptConventions.stepsAttributePath)

            this.stepDisplayManager = stepDisplayManager
            scriptCommander = props.scriptCommander
            clientStateGlobal = props.clientStateGlobal
            mirroredGraphStore = props.mirroredGraphStore
            objectStableMapper = props.objectStableMapper
        }
    }
}