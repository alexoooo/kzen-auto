package tech.kzen.auto.client.objects

import kotlinx.css.*
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.*
import react.dom.h3
import react.dom.input
import react.dom.span
import styled.*
import tech.kzen.auto.client.objects.action.ActionController
import tech.kzen.auto.client.objects.action.ActionCreator
import tech.kzen.auto.client.objects.action.ActionWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.CommandBus
import tech.kzen.auto.client.service.InsertionManager
import tech.kzen.auto.client.service.action.ActionManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.exec.ExecutionModel
import tech.kzen.auto.common.exec.ExecutionState
import tech.kzen.auto.common.service.ExecutionManager
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.auto.common.service.ProjectModel
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.notation.edit.CreateBundleCommand
import tech.kzen.lib.common.notation.edit.NotationCommand
import tech.kzen.lib.common.notation.edit.NotationEvent
import tech.kzen.lib.common.notation.model.BundleNotation
import tech.kzen.lib.common.notation.model.GraphNotation



@Suppress("unused")
class AutoProject(
        props: AutoProject.Props
):
        RComponent<AutoProject.Props, AutoProject.State>(props),
        ModelManager.Observer,
        ExecutionManager.Observer,
        CommandBus.Observer,
        InsertionManager.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var actionManager: ActionManager
    ): RProps

    class State(
            var notation: GraphNotation?,
            var metadata: GraphMetadata?,
            var execution: ExecutionModel?,
//            var runningAll: Boolean,
//            var pending: Boolean = false,
            var commandError: String?,
            var creating: Boolean
    ): RState


    @Suppress("unused")
    class Wrapper(
            private val actionManager: ActionManager
    ): ReactWrapper {
        override fun execute(input: RBuilder): ReactElement {
//            println("^%$^$% AutoProject - $actionManager")

            return input.child(AutoProject::class) {
                attrs {
                    actionManager = this@Wrapper.actionManager
                }
            }
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


    override fun componentDidUpdate(
            prevProps: AutoProject.Props,
            prevState: AutoProject.State,
            snapshot: Any
    ) {
//        console.log("AutoProject componentDidUpdate", state, prevState)

        if (state.notation != null &&
                state.notation!!.bundles.values[NotationConventions.mainPath] == null &&
                (prevState.notation == null ||
                        prevState.notation!!.bundles.values[NotationConventions.mainPath] != null)) {
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
        ClientContext.commandBus.apply(CreateBundleCommand(NotationConventions.mainPath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(autoModel: ProjectModel, event: NotationEvent?) {
//        println("AutoProject - && handled - " +
//                "${autoModel.graphNotation.bundles.values[NotationConventions.mainPath]?.objects?.values?.keys}")

        setState {
            notation = autoModel.graphNotation
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
    override fun onCommandSuccess(command: NotationCommand, event: NotationEvent) {
//        console.log("%%%%%%% onCommandSuccess", command, event)

        setState {
            commandError = null
        }
    }


    override fun onCommandFailedInClient(command: NotationCommand, cause: Throwable) {
//        console.log("%%%%%%% onCommandFailedInClient", command, cause)
        setState {
            commandError = "${cause.message}"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onSelected(action: ObjectLocation) {
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


    // TODO: show on screen, or just do on load?
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

//        console.log("^^^ executionStateToFreshStart", expectedDigest.asString(), actualDigest.asString())

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
                            styledSpan {
                                css {
//                                    display = Display.inlineBlock
                                    float = Float.left
                                }

                                renderLogo()
                            }


                            styledSpan {
                                css {
                                    marginLeft = 1.em
//                                    display = Display.inlineBlock
                                }

                                child(ActionCreator::class) {
                                    attrs {
                                        actionManager = props.actionManager
                                        notation = projectNotation
                                        path = NotationConventions.mainPath
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.commandError != null) {
                    +"!! ERROR: ${state.commandError}"
                }

                val projectPackage: BundleNotation? =
                        projectNotation.bundles.values[NotationConventions.mainPath]

                if (projectPackage == null) {
                    +"Initializing empty project..."
                }
                else {
//                println("AutoProject - the package - ${projectPackage.objects.keys}")
                    styledDiv {
                        css {
                            //                        marginTop = 4.em
                            marginLeft = 1.em
                        }

                        steps(projectNotation, projectPackage, NotationConventions.mainPath)
                    }

                    footer()
                }
            }
        }
    }


    private fun RBuilder.renderLogo() {
        styledA {
            attrs {
                href = "/"
            }

            styledImg(src = "logo.png") {
                css {
                    height = 35.px
                }

                attrs {
                    title = "Kzen (home)"
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.steps(
            projectNotation: GraphNotation,
            projectPackage: BundleNotation,
            bundlePath: BundlePath
    ) {
        if (projectPackage.objects.values.isEmpty()) {
            h3 {
                +"Empty script, please add steps using action bar (above)"
            }

            insertionPoint(0)
        }
        else {
            nonEmptySteps(projectNotation, projectPackage, bundlePath)
        }
    }


    private fun RBuilder.nonEmptySteps(
            projectNotation: GraphNotation,
            projectPackage: BundleNotation,
            bundlePath: BundlePath
    ) {
        insertionPoint(0)

        val graphMetadata = state.metadata!!

        styledDiv {
            css {
                width = 20.em
            }

            val next = state.execution?.next()

            var index = 0
            for (e in projectPackage.objects.values) {
                val status: ExecutionState? =
                        state.execution?.frames?.lastOrNull()?.states?.get(e.key)

                val keyLocation = ObjectLocation(bundlePath, e.key)

                action(keyLocation,
                        projectNotation,
                        graphMetadata,
                        status,
                        next?.bundlePath == bundlePath &&
                                next.objectPath == e.key)

                if (index < projectPackage.objects.values.size - 1) {
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

        insertionPoint(projectPackage.objects.values.size)
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
            objectLocation: ObjectLocation,
            projectNotation: GraphNotation,
            graphMetadata: GraphMetadata,
            executionState: ExecutionState?,
            nextToExecute: Boolean
    ) {
        // todo:
        val actionUiWrapper: ActionWrapper =
                ActionController.Wrapper()

        span {
            key = objectLocation.toReference().asString()

            actionUiWrapper.render(
                    this,

                    objectLocation,
                    projectNotation,
                    graphMetadata,
                    executionState,
                    nextToExecute)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.footer() {
//            reset()
        styledDiv {
            css {
                position = Position.fixed
                bottom = 0.px
                right = 0.px
                marginRight = 2.em
                marginBottom = 2.em
            }

            child(MaterialFab::class) {
                attrs {
                    // TODO
//                    title = "Run"

                    onClick = ::onRunAll

                    style = reactStyle {
                        backgroundColor = Color.yellow
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
