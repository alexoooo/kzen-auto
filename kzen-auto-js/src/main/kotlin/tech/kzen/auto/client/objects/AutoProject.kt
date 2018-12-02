package tech.kzen.auto.client.objects

import kotlinx.css.*
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.*
import react.dom.*
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.action.ActionController
import tech.kzen.auto.client.objects.action.ActionCreator
import tech.kzen.auto.client.objects.action.ActionWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.CommandBus
import tech.kzen.auto.client.service.InsertionManager
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
        ModelManager.Observer,
        ExecutionManager.Observer,
        CommandBus.Observer,
        InsertionManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var notation: ProjectNotation?,
            var metadata: GraphMetadata?,
            var execution: ExecutionModel?,
//            var runningAll: Boolean,
//            var pending: Boolean = false,
            var commandError: String?,
            var creating: Boolean
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
            ClientContext.modelManager.observe(this)
            ClientContext.executionManager.subscribe(this)
            ClientContext.commandBus.observe(this)
            ClientContext.insertionManager.subscribe(this)
        }
    }


    override fun componentWillUnmount() {
//        println("AutoProject - Un-subscribed")
        ClientContext.modelManager.unobserve(this)
        ClientContext.executionManager.unsubscribe(this)
        ClientContext.commandBus.unobserve(this)
        ClientContext.insertionManager.unSubscribe(this)
    }


    override fun componentDidUpdate(prevProps: RProps, prevState: AutoProject.State, snapshot: Any) {
//        console.log("AutoProject componentDidUpdate", state, prevState)

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



    override suspend fun beforeExecution() {}


    override suspend fun onExecutionModel(executionModel: ExecutionModel) {
        setState {
            execution = executionModel
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onCommandSuccess(command: ProjectCommand, event: ProjectEvent) {
//        console.log("%%%%%%% onCommandSuccess", command, event)

        setState {
            commandError = null
        }
    }


    override fun onCommandFailedInClient(command: ProjectCommand, cause: Throwable) {
//        console.log("%%%%%%% onCommandFailedInClient", command, cause)
        setState {
            commandError = "${cause.message}"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onSelected(actionName: String) {
        setState {
            creating = true
        }
    }


    override fun onUnselected() {
        setState {
            creating = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onCreate(index: Int) {
        async {
            ClientContext.insertionManager.create(
                    NotationConventions.mainPath,
                    index)
        }
    }


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

        val expectedDigest = ClientContext.executionManager.start(
                NotationConventions.mainPath, projectModel)

        val actualDigest = ClientContext.restClient.startExecution()

        console.log("^^^ executionStateToFreshStart", expectedDigest.encode(), actualDigest.encode())

        if (expectedDigest != actualDigest) {
            console.log("Digest mismatch, refreshing")
            onRefresh()
        }
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
        if (projectPackage.objects.isEmpty()) {
            h3 {
                +"Empty script, please add steps using action bar (above)"
            }

            insertionPoint(0)
        }
        else {
            nonEmptySteps(projectNotation, projectPackage)
        }
    }


    private fun RBuilder.nonEmptySteps(
            projectNotation: ProjectNotation,
            projectPackage: PackageNotation
    ) {
        insertionPoint(0)

        val graphMetadata = state.metadata!!

        div(classes = "actionColumn") {
            val next = state.execution?.next()

            var index = 0
            for (e in projectPackage.objects) {
                val status: ExecutionStatus? =
                        state.execution?.frames?.lastOrNull()?.values?.get(e.key)

                action(
                        e.key,
                        projectNotation,
                        graphMetadata,
                        status,
                        next == e.key)

                if (index < projectPackage.objects.size - 1) {
                    styledDiv {
                        css {
                            marginTop =  0.5.em
                            float = Float.left
                        }
                        insertionPoint(index + 1)
                    }

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

                index++
            }
        }

        insertionPoint(projectPackage.objects.size)
    }


    private fun RBuilder.insertionPoint(index: Int) {
        styledSpan {
            attrs {
                title = "Insert action here"
            }

            child(MaterialIconButton::class) {
                attrs {
                    style = reactStyle {
                        if (! state.creating) {
                            opacity = 0
                            cursor = Cursor.default
                        }

//                    marginLeft = 1.em
                    }

                    onClick = {
                        onCreate(index)
                    }
                }

                child(AddCircleOutlineIcon::class) {}
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
