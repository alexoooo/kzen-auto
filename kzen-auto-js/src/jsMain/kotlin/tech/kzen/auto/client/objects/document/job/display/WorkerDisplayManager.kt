package tech.kzen.auto.client.objects.document.job.display

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


//---------------------------------------------------------------------------------------------------------------------
external interface WorkerDisplayManagerProps: Props {
    var workerDisplays: List<WorkerDisplayWrapper>
    var common: WorkerDisplayPropsCommon
    var clientStateGlobal: ClientStateGlobal
}


external interface WorkerDisplayManagerState: State {
    var workerDisplayWrapper: WorkerDisplayWrapper?
}


//---------------------------------------------------------------------------------------------------------------------
// Resolves and mounts a Worker's own declared card component from the `display:` marker on its notation — an
// autowired List<WorkerDisplayWrapper>, matched by name() — so JobController / JobObjectSlot never switch on Worker
// type. A near-verbatim copy of StepDisplayManager, minus the Handle (Workers don't nest, so there is no reference
// cycle to break). A new / 3rd-party Worker declares `display: MyWorkerDisplay`; its `is: WorkerDisplay` object is
// autowired in and resolved here with no edit to this manager (see CC-17).
class WorkerDisplayManager(
    props: WorkerDisplayManagerProps
):
    RPureComponent<WorkerDisplayManagerProps, WorkerDisplayManagerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val workerDisplays: List<WorkerDisplayWrapper>,
        @Service private val clientStateGlobal: ClientStateGlobal
    ):
        ReactWrapper<WorkerDisplayManagerProps>
    {
        override fun ChildrenBuilder.child(block: WorkerDisplayManagerProps.() -> Unit) {
            WorkerDisplayManager::class.react {
                workerDisplays = this@Wrapper.workerDisplays
                clientStateGlobal = this@Wrapper.clientStateGlobal
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun WorkerDisplayManagerState.init(props: WorkerDisplayManagerProps) {
        workerDisplayWrapper = findDisplayWrapper(props)
    }


    private fun findDisplayWrapper(props: WorkerDisplayManagerProps): WorkerDisplayWrapper {
        val graphStructure = props.clientStateGlobal.current()?.graphStructure()
            ?: throw IllegalStateException("Session not initialized")

        val displayWrapperName = ObjectName(
            graphStructure.graphNotation.getString(
                props.common.objectLocation, AutoConventions.displayAttributePath))

        return props.workerDisplays.find { it.name() == displayWrapperName }
            ?: throw IllegalStateException("Worker display not found: $displayWrapperName")
    }


    override fun componentDidUpdate(
        prevProps: WorkerDisplayManagerProps,
        prevState: WorkerDisplayManagerState,
        snapshot: Any
    ) {
        if (props.common.objectLocation == prevProps.common.objectLocation) {
            return
        }

        setState {
            workerDisplayWrapper = findDisplayWrapper(props)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val display = state.workerDisplayWrapper
            ?: return

        display.child(this) {
            common = props.common
        }
    }
}
