package tech.kzen.auto.client.objects

import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import react.*
import react.dom.*
import tech.kzen.auto.client.service.*
import tech.kzen.auto.client.ui.ActionController
import tech.kzen.auto.client.ui.ActionCreator
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.exec.ExecutionStatus
import tech.kzen.auto.common.service.ExecutionManager
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.auto.common.service.ProjectModel
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
    // todo: manage dynamically
    val projectPath = ProjectPath("notation/dummy/dummy.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    class State(
            var notation: ProjectNotation?,
            var metadata: GraphMetadata?,
            var execution: ExecutionModel?,
            var runningAll: Boolean
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


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleModel(autoModel: ProjectModel, event: ProjectEvent?) {
        println("AutoProject - && handled - ${autoModel.projectNotation.packages[projectPath]!!.objects.keys}")

//        async {
            setState {
                notation = autoModel.projectNotation
                metadata = autoModel.graphMetadata
            }
//        }
    }


    override fun handleExecution(executionModel: ExecutionModel) {
        setState {
            execution = executionModel
        }

        println("&&&&&&&&&&&&&&&&&&& state.runningAll: ${state.runningAll}")
        val next = executionModel.next()
//        if (state.runningAll) {
        if (next != null) {

            println("&&&&&&&&&&&&&&&&&&& next: $next")
//
//            if (next != null) {
                async {
                    var success = false

                    try {
                        ClientContext.restClient.performAction(next)
                        success = true
                    }
                    catch (e: Exception) {
                        println("#$%#$%#$ got exception: $e")
                    }

                    // TODO: factor out and consolidate
                    ClientContext.executionManager.onExecution(next, success)
                }
//            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onReload() {
        async {
            ClientContext.modelManager.refresh()
        }
    }


    private fun onReset() {
        setState {
            runningAll = false
        }

        ClientContext.executionManager.reset()
    }


    private fun onRunAll() {
        setState {
            runningAll = true
        }

        val projectModel = ProjectModel(
                state.notation!!,
                state.metadata!!)

        ClientContext.executionManager.start(projectPath, projectModel)
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
                    projectNotation.packages[projectPath]

            if (projectPackage == null) {
                +"Please provide project package"
            }
            else {
                println("AutoProject - the package - ${projectPackage.objects.keys}")

                div(classes = "child") {
                    h3 {
                        +"Steps:"
                    }

                    val graphMetadata = state.metadata!!

                    div(classes = "actionColumn") {

                        val next = state.execution?.next()

                        for (e in projectPackage.objects) {

                            val status: ExecutionStatus? =
                                    state.execution?.frames?.lastOrNull()?.values?.get(e.key)

                            renderAction(
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
                                path = projectPath
                            }
                        }
                    }


                    // TODO: compensate for footer
                    br {}
                    br {}
                    br {}
                }

                div(classes = "footer") {
                    renderRunAll()
                    renderReset()

                    +" | "

                    renderRefresh()
                }
            }
        }
    }


    private fun RBuilder.renderAction(
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


    private fun RBuilder.renderRunAll() {
        input (type = InputType.button) {
            attrs {
                value = "Run All"
                onClickFunction = { onRunAll() }
            }
        }
    }


    private fun RBuilder.renderReset() {
        input (type = InputType.button) {
            attrs {
                value = "Reset"
                onClickFunction = { onReset() }
            }
        }
    }


    private fun RBuilder.renderRefresh() {
        input (type = InputType.button) {
            attrs {
                value = "Refresh"
                onClickFunction = { onReload() }
            }
        }
    }
}
