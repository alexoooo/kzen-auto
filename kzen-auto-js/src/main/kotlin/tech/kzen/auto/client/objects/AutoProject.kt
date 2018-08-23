package tech.kzen.auto.client.objects

import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import kotlinx.coroutines.experimental.*
import react.*
import react.dom.*
import tech.kzen.auto.client.service.*
import tech.kzen.auto.client.ui.ActionController
import tech.kzen.auto.client.ui.ActionCreator
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.exec.ExecutionStatus
import tech.kzen.auto.common.notation.NotationConventions
import tech.kzen.auto.common.service.ExecutionManager
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.auto.common.service.ProjectModel
import tech.kzen.lib.common.edit.CreatePackageCommand
import tech.kzen.lib.common.edit.ProjectEvent
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.*



@Suppress("unused")
class AutoProject:
        RComponent<RProps, AutoProject.State>(),
        ModelManager.Subscriber,
        ExecutionManager.Subscriber
{
    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var notation: ProjectNotation?,
            var metadata: GraphMetadata?,
            var execution: ExecutionModel?,
            var runningAll: Boolean,
            var pending: Boolean = false
    ) : RState


    @Suppress("unused")
    class Wrapper: ReactWrapper {
        override fun execute(input: RBuilder): ReactElement {
            return input.child(AutoProject::class) {}
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
//        println("AutoProject - Subscribed")
        ClientContext.modelManager.subscribe(this)
        ClientContext.executionManager.subscribe(this)
    }


    override fun componentWillUnmount() {
//        println("AutoProject - Un-subscribed")
        ClientContext.modelManager.unsubscribe(this)
        ClientContext.executionManager.unsubscribe(this)
    }


    override fun componentDidUpdate(prevProps: RProps, prevState: AutoProject.State, snapshot: Any) {
        console.log("AutoProject componentDidUpdate", state, prevState)

        if (state.notation != null &&
                state.notation!!.packages[NotationConventions.mainPath] == null &&
                (prevState.notation == null || prevState.notation!!.packages[NotationConventions.mainPath] != null)) {
            async {
                createMain()
            }
            return
        }

        if (state.execution == null) {
            return
        }

        if (state.execution!!.frames.isEmpty()) {
            console.log("!@#!#!@#!@#!@  starting execution")
            executionStateToFreshStart()
            return
        }

        if (! state.runningAll) {
            return
        }

        // only look at transition from pending to non-pending state?
        if (state.pending /*|| ! prevState.pending*/) {
//            console.log("AutoProject PENDING", state.pending, prevState.pending)
            return
        }

        val next = state.execution!!.next()
                ?: return

        setState {
            pending = true
        }

        async {
            runNext(next)
        }
    }


    private suspend fun createMain() {
        ClientContext.commandBus.apply(CreatePackageCommand(NotationConventions.mainPath))
    }

    private suspend fun runNext(next: String) {
        // TODO: factor out and consolidate
        ClientContext.executionManager.willExecute(next)

        delay(250)

        var success = false
        try {
            ClientContext.restClient.performAction(next)
            success = true
        }
        catch (e: Exception) {
            println("#$%#$%#$ got exception: $e")

            setState {
                runningAll = false
            }
        }

        ClientContext.executionManager.didExecute(next, success)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleModel(autoModel: ProjectModel, event: ProjectEvent?) {
        println("AutoProject - && handled - " +
                "${autoModel.projectNotation.packages[NotationConventions.mainPath]?.objects?.keys}")

        setState {
            notation = autoModel.projectNotation
            metadata = autoModel.graphMetadata
            pending = false
        }
    }


    override fun handleExecution(executionModel: ExecutionModel) {
        val next = executionModel.next()

        val nextRunning =
                if (next == null) {
                    false
                }
                else {
                    executionModel.containsStatus(ExecutionStatus.Running)
                }

        setState {
            execution = executionModel
            pending = nextRunning
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRefresh() {
        async {
            ClientContext.modelManager.refresh()
            ClientContext.executionManager.reset()
        }
    }


    private fun onClear() {
        setState {
            runningAll = false
            pending = false
        }

        ClientContext.executionManager.reset()
        executionStateToFreshStart()
    }


    private fun onRunAll() {
        executionStateToFreshStart()

        setState {
            runningAll = true
            pending = false
        }
    }


    private fun executionStateToFreshStart() {
        val projectModel = ProjectModel(
                state.notation!!,
                state.metadata!!)

        ClientContext.executionManager.start(NotationConventions.mainPath, projectModel)
    }



    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val projectNotation = state.notation
        if (projectNotation == null) {
            +"Loading..."
        }
        else {
            println("AutoProject - Available packages: ${projectNotation.packages.keys}")

            val projectPackage: PackageNotation? =
                    projectNotation.packages[NotationConventions.mainPath]

            if (projectPackage == null) {
                +"Initializing empty project..."
            }
            else {
                println("AutoProject - the package - ${projectPackage.objects.keys}")

                div(classes = "child") {
                    steps(projectNotation, projectPackage)
                }

                footer()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.steps(
            projectNotation: ProjectNotation,
            projectPackage: PackageNotation
    ) {
        h3 {
            +"Steps:"
        }

        val graphMetadata = state.metadata!!

        div(classes = "actionColumn") {

            val next = state.execution?.next()

            for (e in projectPackage.objects) {

                val status: ExecutionStatus? =
                        state.execution?.frames?.lastOrNull()?.values?.get(e.key)

                action(
                        e.key,
                        projectNotation,
                        graphMetadata,
                        status,
                        next == e.key)

                img(src = "arrow-pointing-down.png", classes = "downArrow") {}
            }

            child(ActionCreator::class) {
                attrs {
                    notation = projectNotation
                    path = NotationConventions.mainPath
                }
            }
        }
    }

    private fun RBuilder.action(
            objectName: String,
            projectNotation: ProjectNotation,
            graphMetadata: GraphMetadata,
            executionStatus: ExecutionStatus?,
            nextToExecute: Boolean
    ) {
        child(ActionController::class) {
            key = objectName

            attrs {
                name = objectName

                notation = projectNotation
                metadata = graphMetadata

                status = executionStatus
                next = nextToExecute
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.footer() {
        // TODO: compensate for footer
        br {}
        br {}
        br {}


        div(classes = "footer") {
            runAll()
            reset()

            +" | "

            refresh()
        }
    }


    private fun RBuilder.runAll() {
        input (type = InputType.button) {
            attrs {
                value = "Run All"
                onClickFunction = { onRunAll() }
            }
        }
    }


    private fun RBuilder.reset() {
        input (type = InputType.button) {
            attrs {
                value = "Clear"
                onClickFunction = { onClear() }
            }
        }
    }


    private fun RBuilder.refresh() {
        input (type = InputType.button) {
            attrs {
                value = "Reload"
                onClickFunction = { onRefresh() }
            }
        }
    }
}
