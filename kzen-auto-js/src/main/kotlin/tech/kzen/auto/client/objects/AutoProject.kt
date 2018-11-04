package tech.kzen.auto.client.objects

import kotlinx.css.Color
import kotlinx.css.Display
import kotlinx.css.LinearDimension
import kotlinx.css.em
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import react.*
import react.dom.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.action.ActionController
import tech.kzen.auto.client.objects.action.ActionCreator
import tech.kzen.auto.client.objects.action.ActionWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.CommandBus
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.exec.ExecutionStatus
import tech.kzen.auto.common.service.ExecutionManager
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.auto.common.service.ProjectModel
import tech.kzen.lib.common.edit.CreatePackageCommand
import tech.kzen.lib.common.edit.ProjectCommand
import tech.kzen.lib.common.edit.ProjectEvent
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.notation.model.PackageNotation
import tech.kzen.lib.common.notation.model.ProjectNotation



@Suppress("unused")
class AutoProject :
        RComponent<RProps, AutoProject.State>(),
        ModelManager.Subscriber,
        ExecutionManager.Subscriber,
        CommandBus.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var notation: ProjectNotation?,
            var metadata: GraphMetadata?,
            var execution: ExecutionModel?,
//            var runningAll: Boolean,
//            var pending: Boolean = false,
            var commandError: String?
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
        async {
            ClientContext.modelManager.subscribe(this)
            ClientContext.executionManager.subscribe(this)
            ClientContext.commandBus.observe(this)
        }
    }


    override fun componentWillUnmount() {
//        println("AutoProject - Un-subscribed")
        ClientContext.modelManager.unsubscribe(this)
        ClientContext.executionManager.unsubscribe(this)
        ClientContext.commandBus.unobserve(this)
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
//            console.log("!@#!#!@#!@#!@  starting execution")
            async {
                executionStateToFreshStart()
            }
            return
        }
    }


    private suspend fun createMain() {
        ClientContext.commandBus.apply(CreatePackageCommand(NotationConventions.mainPath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(autoModel: ProjectModel, event: ProjectEvent?) {
        println("AutoProject - && handled - " +
                "${autoModel.projectNotation.packages[NotationConventions.mainPath]?.objects?.keys}")

        setState {
            notation = autoModel.projectNotation
            metadata = autoModel.graphMetadata
//            pending = false
        }
    }



    override suspend fun beforeExecution(executionModel: ExecutionModel) {}


    override suspend fun afterExecution(executionModel: ExecutionModel) {
        setState {
            execution = executionModel
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onCommandSuccess(command: ProjectCommand, event: ProjectEvent) {
        console.log("%%%%%%% onCommandSuccess", command, event)

        setState {
            commandError = null
        }
    }


    override fun onCommandFailedInClient(command: ProjectCommand, cause: Throwable) {
        console.log("%%%%%%% onCommandFailedInClient", command, cause)
        setState {
            commandError = "${cause.message}"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRefresh() {
        ClientContext.executionLoop.pause()

        async {
            ClientContext.modelManager.refresh()
            ClientContext.executionManager.reset()
        }
    }


    private fun onClear() {
        ClientContext.executionLoop.pause()

        async {
            ClientContext.executionManager.reset()
            executionStateToFreshStart()
        }
    }


    private fun onRunAll() {
        async {
//            println("AutoProject | onRunAll")

            executionStateToFreshStart()

//            println("AutoProject | after executionStateToFreshStart")

            ClientContext.executionLoop.run()
        }
    }


    private suspend fun executionStateToFreshStart() {
        val projectModel = ProjectModel(
                state.notation!!,
                state.metadata!!)

        ClientContext.executionManager.start(NotationConventions.mainPath, projectModel)
    }



    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
//                backgroundColor = Color("rgb(225, 225, 225)")
            }

            val projectNotation = state.notation
            if (projectNotation == null) {
                +"Loading..."
            }
            else {

                child(MaterialAppBar::class) {
                    attrs {
                        position = "sticky"

                        style = reactStyle {
                            backgroundColor = Color.white
                        }
                    }

                    child(MaterialToolbar::class) {
                        child(MaterialTypography::class) {
                            +"Kzen | "

                            child(ActionCreator::class) {
                                attrs {
                                    notation = projectNotation
                                    path = NotationConventions.mainPath
                                }
                            }
                        }
                    }
                }

                if (state.commandError != null) {
                    +"!! ERROR: ${state.commandError}"
                }

                val projectPackage: PackageNotation? =
                        projectNotation.packages[NotationConventions.mainPath]

                if (projectPackage == null) {
                    +"Initializing empty project..."
                }
                else {
//                println("AutoProject - the package - ${projectPackage.objects.keys}")


//                rgb(225, 225, 225)

                    styledDiv {
                        css {
                            //                        marginTop = 4.em
                            marginLeft = 1.em
                        }

                        steps(projectNotation, projectPackage)
                    }

                    footer()
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.steps(
            projectNotation: ProjectNotation,
            projectPackage: PackageNotation
    ) {
        h3 {
            +"Steps (${projectPackage.objects.size}):"
        }

        val graphMetadata = state.metadata!!

        div(classes = "actionColumn") {
            val next = state.execution?.next()

            var isFirst = true

            for (e in projectPackage.objects) {
                val status: ExecutionStatus? =
                        state.execution?.frames?.lastOrNull()?.values?.get(e.key)

                if (! isFirst) {
                    styledDiv {
                        css {
                            marginLeft = LinearDimension.auto
                            marginRight = LinearDimension.auto
                            display = Display.block

                            width = 3.em
//                        height = 2.em
                            marginTop =  0.5.em
                            marginBottom = 0.5.em
                        }

                        child(ArrowDownwardIcon::class) {
                            attrs {
                                style = reactStyle {
                                    fontSize = 3.em
                                }
                            }
                        }
                    }
                }

                action(
                        e.key,
                        projectNotation,
                        graphMetadata,
                        status,
                        next == e.key)

                isFirst = false
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
        // todo:
        val actionUiWrapper: ActionWrapper =
                ActionController.Wrapper()

        span {
            key = objectName

            actionUiWrapper.render(
                    this,

                    objectName,
                    projectNotation,
                    graphMetadata,
                    executionStatus,
                    nextToExecute)


//            child(ActionController::class) {
//                attrs {
//                    name = objectName
//
//                    notation = projectNotation
//                    metadata = graphMetadata
//
//                    status = executionStatus
//                    next = nextToExecute
//                }
//            }
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
