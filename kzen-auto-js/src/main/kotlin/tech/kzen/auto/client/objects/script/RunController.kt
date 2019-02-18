package tech.kzen.auto.client.objects.script

import kotlinx.css.Color
import kotlinx.css.em
import react.*
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.MaterialFab
import tech.kzen.auto.client.wrap.PlayArrowIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.service.ExecutionManager
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.NotationEvent


class RunController:
        RComponent<RProps, RunController.State>(),
        ModelManager.Observer,
        ExecutionManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var structure: GraphStructure?,
            var execution: ExecutionModel?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(autoModel: GraphStructure, event: NotationEvent?) {
        setState {
            structure = autoModel
        }
    }


    override suspend fun beforeExecution(objectLocation: ObjectLocation) {}


    override suspend fun onExecutionModel(executionModel: ExecutionModel) {
        setState {
            execution = executionModel
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
            ClientContext.modelManager.observe(this)
            ClientContext.executionManager.subscribe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.modelManager.unobserve(this)
        ClientContext.executionManager.unsubscribe(this)
    }


    override fun componentDidUpdate(
            prevProps: RProps,
            prevState: RunController.State,
            snapshot: Any
    ) {
//        console.log("ProjectController componentDidUpdate", state, prevState)
        if (state.execution == null) {
            return
        }

        if (state.execution!!.frames.isEmpty()) {
//            console.log("!@#!#!@#!@#!@  starting execution")
            async {
                executionStateToFreshStart()
            }
            return
        }
    }


    //-----------------------------------------------------------------------------------------------------------------

    // TODO: refresh manager?
    private fun onRefresh() {
        ClientContext.executionLoop.pause()

        async {
            ClientContext.modelManager.refresh()
            ClientContext.executionManager.reset()
        }
    }


    private suspend fun executionStateToFreshStart() {
        val expectedDigest = ClientContext.executionManager.start(
                NotationConventions.mainPath, state.structure!!)

        val actualDigest = ClientContext.restClient.startExecution()

//        console.log("^^^ executionStateToFreshStart", expectedDigest.asString(), actualDigest.asString())

        if (expectedDigest != actualDigest) {
            // TODO
            console.log("Digest mismatch, refresh required")
            // onRefresh()
        }
    }



    //-----------------------------------------------------------------------------------------------------------------

    private fun onClear() {
        ClientContext.executionLoop.pause()

        async {
            ClientContext.executionManager.reset()
            executionStateToFreshStart()
        }
    }


    private fun onRunAll() {
        async {
            //            println("ProjectController | onRunAll")

            executionStateToFreshStart()

//            println("ProjectController | after executionStateToFreshStart")

            ClientContext.executionIntent.clear()
            ClientContext.executionLoop.run()
        }
    }


    private fun onRunAllEnter() {
        val nextToRun = state.execution?.next()
//        println("^$%^$%^% onRunAllEnter - ${state.execution} - $nextToRun")
        if (nextToRun != null) {
            ClientContext.executionIntent.set(nextToRun)
        }
    }


    private fun onRunAllLeave() {
        val nextToRun = state.execution?.next()
//        println("^$%^$%^% onRunAllLeave - $nextToRun")
        if (nextToRun != null) {
            ClientContext.executionIntent.clearIf(nextToRun)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        child(MaterialFab::class) {
            attrs {
                onClick = ::onRunAll
                onMouseOver = ::onRunAllEnter
                onMouseOut = ::onRunAllLeave

                style = reactStyle {
                    backgroundColor = Color.gold
                    width = 5.em
                    height = 5.em
                }
            }

            child(PlayArrowIcon::class) {
                attrs {
                    style = reactStyle {
                        fontSize = 3.em
                    }
                }
            }
        }
    }
}