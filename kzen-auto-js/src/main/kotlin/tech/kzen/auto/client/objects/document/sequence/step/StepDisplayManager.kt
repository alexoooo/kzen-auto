package tech.kzen.auto.client.objects.document.sequence.step

import react.ChildrenBuilder
import react.Props
import react.State
import react.react
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.sequence.step.display.SequenceStepDisplayPropsCommon
import tech.kzen.auto.client.objects.document.sequence.step.display.SequenceStepDisplayWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.store.LocalGraphStore
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface StepDisplayManagerProps: Props {
    var stepDisplays: List<SequenceStepDisplayWrapper>
    var common: SequenceStepDisplayPropsCommon
}


//---------------------------------------------------------------------------------------------------------------------
class StepDisplayManager(
    props: StepDisplayManagerProps
):
    RPureComponent<StepDisplayManagerProps, State>(props), LocalGraphStore.Observer {
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
    private var sequenceStepDisplayWrapper: SequenceStepDisplayWrapper? = null


    override fun componentDidMount() {
        if (sequenceStepDisplayWrapper != null) {
            return
        }

        async {
            ClientContext.mirroredGraphStore.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.mirroredGraphStore.unobserve(this)
    }


    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}


    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        onStoreRefresh(graphDefinition)
    }


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {
        if (sequenceStepDisplayWrapper != null) {
            return
        }

        val displayWrapperName = ObjectName(
            graphDefinition.graphStructure.graphNotation.getString(
                props.common.objectLocation, AutoConventions.displayAttributePath))

        sequenceStepDisplayWrapper = props.stepDisplays.find { it.name() == displayWrapperName }
            ?: throw IllegalStateException("Step display not found: $displayWrapperName")
    }


    //------------------------------------------------------------------ -----------------------------------------------
    override fun ChildrenBuilder.render() {
        val display = sequenceStepDisplayWrapper
            ?: return

//        +"[sequenceStepDisplayWrapper - ${sequenceStepDisplayWrapper?.name()}] - ${props.common}"
        display.child(this) {
            common = props.common
        }
    }
}