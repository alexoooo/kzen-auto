package tech.kzen.auto.client.objects.document.script.display

import react.ChildrenBuilder
import react.Props
import react.State
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface StepDisplayManagerProps: Props {
    var stepDisplays: List<ScriptStepDisplayWrapper>
    var common: ScriptStepDisplayPropsCommon
    var clientStateGlobal: ClientStateGlobal
}


external interface StepDisplayManagerState: State {
    var scriptStepDisplayWrapper: ScriptStepDisplayWrapper?
}


//---------------------------------------------------------------------------------------------------------------------
class StepDisplayManager(
    props: StepDisplayManagerProps
):
    RPureComponent<StepDisplayManagerProps, StepDisplayManagerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val width = 26.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val stepDisplays: List<ScriptStepDisplayWrapper>,
        handle: Handle,
        @Service private val clientStateGlobal: ClientStateGlobal
    ):
        ReactWrapper<StepDisplayManagerProps>
    {
        init {
            handle.wrapper = this
        }

        override fun ChildrenBuilder.child(block: StepDisplayManagerProps.() -> Unit) {
            StepDisplayManager::class.react {
                stepDisplays = this@Wrapper.stepDisplays
                clientStateGlobal = this@Wrapper.clientStateGlobal
                block()
            }
        }
    }


    /**
     * NB: lazy reference to avoid reference cycle with nested steps
     */
    @Reflect
    class Handle {
        var wrapper: Wrapper? = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun StepDisplayManagerState.init(props: StepDisplayManagerProps) {
        scriptStepDisplayWrapper = findDisplayWrapper(props)
    }


    private fun findDisplayWrapper(props: StepDisplayManagerProps): ScriptStepDisplayWrapper {
        val graphStructure = props.clientStateGlobal.current()?.graphStructure()
            ?: throw IllegalStateException("Session not initialized")

        val displayWrapperName = ObjectName(
            graphStructure.graphNotation.getString(
                props.common.objectLocation, AutoConventions.displayAttributePath))

        return props.stepDisplays.find { it.name() == displayWrapperName }
            ?: throw IllegalStateException("Step display not found: $displayWrapperName")
    }


    override fun componentDidUpdate(
        prevProps: StepDisplayManagerProps,
        prevState: StepDisplayManagerState,
        snapshot: Any
    ) {
        if (props.common.objectLocation == prevProps.common.objectLocation) {
            return
        }

        setState {
            scriptStepDisplayWrapper = findDisplayWrapper(props)
        }
    }


    //------------------------------------------------------------------ -----------------------------------------------
    override fun ChildrenBuilder.render() {
        val display = state.scriptStepDisplayWrapper
            ?: return

//        +"[scriptStepDisplayWrapper - ${scriptStepDisplayWrapper?.name()}] - ${props.common}"
        display.child(this) {
            common = props.common
        }
    }
}