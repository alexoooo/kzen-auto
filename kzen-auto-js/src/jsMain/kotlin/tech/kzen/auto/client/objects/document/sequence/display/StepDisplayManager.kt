package tech.kzen.auto.client.objects.document.sequence.display

import react.ChildrenBuilder
import react.Props
import react.State
import react.react
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.reflect.Reflect
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface StepDisplayManagerProps: Props {
    var stepDisplays: List<SequenceStepDisplayWrapper>
    var common: SequenceStepDisplayPropsCommon
}


external interface StepDisplayManagerState: State {
    var sequenceStepDisplayWrapper: SequenceStepDisplayWrapper?
}


//---------------------------------------------------------------------------------------------------------------------
class StepDisplayManager(
    props: StepDisplayManagerProps
):
    RPureComponent<StepDisplayManagerProps, StepDisplayManagerState>(props)//,
//    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val width = 26.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val stepDisplays: List<SequenceStepDisplayWrapper>,
        handle: Handle
    ):
        ReactWrapper<StepDisplayManagerProps>
    {
        init {
            handle.wrapper = this
        }

        override fun ChildrenBuilder.child(block: StepDisplayManagerProps.() -> Unit) {
            StepDisplayManager::class.react {
                stepDisplays = this@Wrapper.stepDisplays
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
        sequenceStepDisplayWrapper = findDisplayWrapper(props)
    }


    private fun findDisplayWrapper(props: StepDisplayManagerProps): SequenceStepDisplayWrapper {
        val graphStructure = ClientContext.clientStateGlobal.current()?.graphStructure()
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
            sequenceStepDisplayWrapper = findDisplayWrapper(props)
        }
    }


    //------------------------------------------------------------------ -----------------------------------------------
    override fun ChildrenBuilder.render() {
        val display = state.sequenceStepDisplayWrapper
            ?: return

//        +"[sequenceStepDisplayWrapper - ${sequenceStepDisplayWrapper?.name()}] - ${props.common}"
        display.child(this) {
            common = props.common
        }
    }
}