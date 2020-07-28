package tech.kzen.auto.client.objects.document.process

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.client.objects.document.process.state.ProcessStore
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Suppress("unused")
class ProcessController(
    props: Props
):
    RPureComponent<ProcessController.Props, ProcessController.State>(props),
    ProcessStore.Subscriber
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props: RProps


    class State(
        var processState: ProcessState?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
            private val archetype: ObjectLocation
    ):
            DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }

        override fun child(input: RBuilder, handler: RHandler<RProps>): ReactElement {
            return input.child(ProcessController::class) {
                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val store = ProcessStore()
//    private var mounted = false


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        processState = null
    }


    override fun componentDidMount() {
        store.didMount(this)
    }


    override fun componentWillUnmount() {
        store.willUnmount()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onProcessState(processState: ProcessState?) {
        setState {
            this.processState = processState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val processState = state.processState
            ?: return

        styledDiv {
            css {
                padding(1.em)
            }

            renderInput(processState)
            renderFilter(processState)
            renderOutput(processState)
        }

        renderRun(processState)
    }


    private fun RBuilder.renderInput(processState: ProcessState) {
        child(ProcessInput::class) {
            attrs {
                this.processState = processState
                dispatcher = store
            }
        }
    }


    private fun RBuilder.renderFilter(processState: ProcessState) {
        child(ProcessFilter::class) {
            attrs {
                this.processState = processState
            }
        }
    }


    private fun RBuilder.renderOutput(processState: ProcessState) {
        child(ProcessOutput::class) {
            attrs {
                this.processState = processState
            }
        }
    }


    private fun RBuilder.renderRun(processState: ProcessState) {
        styledDiv {
            css {
                position = Position.fixed
                bottom = 0.px
                right = 0.px
                marginRight = 2.em
                marginBottom = 2.em
            }

            child(ProcessRun::class) {
                attrs {
                    this.processState = processState
                }
            }
        }
    }
}