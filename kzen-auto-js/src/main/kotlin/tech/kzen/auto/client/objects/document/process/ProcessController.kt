package tech.kzen.auto.client.objects.document.process

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.StageController
import tech.kzen.auto.client.objects.document.process.filter.ProcessFilterList
import tech.kzen.auto.client.objects.document.process.state.ProcessState
import tech.kzen.auto.client.objects.document.process.state.ProcessStore
import tech.kzen.auto.client.wrap.MaterialLinearProgress
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Suppress("unused")
class ProcessController(
    props: RProps
):
    RPureComponent<RProps, ProcessController.State>(props),
    ProcessStore.Subscriber
{
    //-----------------------------------------------------------------------------------------------------------------
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
//        override fun child(input: RBuilder, handler: RHandler<DocumentControllerProps>): ReactElement {
            return input.child(ProcessController::class) {
                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val store = ProcessStore()


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: RProps) {
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
//        console.log("^^^^^ onProcessState!!")

        setState {
            this.processState = processState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val processState = state.processState
            ?: return

        renderInitialLoading(processState)

        styledDiv {
            css {
                padding(3.em)
            }

            if (processState.nextErrorMessage() != null) {
                styledDiv {
                    css {
                        margin(1.em)
                        color =  Color.crimson
                        fontWeight = FontWeight.bold
                    }
                    +"Error: ${processState.nextErrorMessage()}"
                }
            }

            renderInput(processState)
            renderFilter(processState)
            renderOutput(processState)
        }

        renderRun(processState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderInitialLoading(processState: ProcessState) {
        if (! processState.initiating) {
            return
        }

        StageController.StageContext.Consumer { context ->
            styledDiv {
                css {
                    position = Position.fixed
                    top = context.stageTop
                    left = context.stageLeft
                    width = 100.pct
                    zIndex = 99
                }

                child(MaterialLinearProgress::class) {}
            }
        }
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
        child(ProcessFilterList::class) {
            attrs {
                this.processState = processState
                this.dispatcher = store
            }
        }
    }


    private fun RBuilder.renderOutput(processState: ProcessState) {
        child(ProcessOutput::class) {
            attrs {
                this.processState = processState
                this.dispatcher = store
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
                    dispatcher = store
                }
            }
        }
    }
}