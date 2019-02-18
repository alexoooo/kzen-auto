package tech.kzen.auto.client.objects

import kotlinx.css.*
import kotlinx.html.title
import react.*
import styled.*
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.script.RunController
import tech.kzen.auto.client.objects.script.ScriptController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.CommandBus
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.MaterialAppBar
import tech.kzen.auto.client.wrap.MaterialToolbar
import tech.kzen.auto.client.wrap.MaterialTypography
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.NotationCommand
import tech.kzen.lib.common.structure.notation.edit.NotationEvent


@Suppress("unused")
class ProjectController(
        props: ProjectController.Props
):
        RComponent<ProjectController.Props, ProjectController.State>(props),
        ModelManager.Observer,
        CommandBus.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
//            var actionManager: ActionManager
            var ribbonController: RibbonController.Wrapper
    ): RProps

    class State(
            var structure: GraphStructure?,
            var commandError: String?
    ): RState


    @Suppress("unused")
    class Wrapper(
//            private val actionManager: ActionManager
            private val ribbonController: RibbonController.Wrapper
    ): ReactWrapper<ProjectController.Props> {
        override fun child(input: RBuilder, handler: RHandler<ProjectController.Props>): ReactElement {
//            println("^%$^$% ProjectController - $actionManager")
            return input.child(ProjectController::class) {
                attrs {
//                    actionManager = this@Wrapper.actionManager
                    ribbonController = this@Wrapper.ribbonController
                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
//        println("ProjectController - Subscribed")
        async {
            ClientContext.modelManager.observe(this)
            ClientContext.commandBus.observe(this)
        }
    }


    override fun componentWillUnmount() {
//        println("ProjectController - Un-subscribed")
        ClientContext.modelManager.unobserve(this)
        ClientContext.commandBus.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun handleModel(autoModel: GraphStructure, event: NotationEvent?) {
//        println("ProjectController - && handled - " +
//                "${autoModel.graphNotation.bundles.values[NotationConventions.mainPath]?.objects?.values?.keys}")

        setState {
            structure = autoModel
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
    override fun RBuilder.render() {
        styledDiv {
            val projectNotation = state.structure?.graphNotation
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
                                    float = Float.left
                                }

                                renderLogo()
                            }


                            styledSpan {
                                css {
                                    marginLeft = 1.em
                                }

                                props.ribbonController.child(this) {
                                    attrs {
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

                child(ScriptController::class) {
                    attrs {
                        bundlePath = NotationConventions.mainPath
                    }
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
    private fun RBuilder.runController() {
        styledDiv {
            css {
                position = Position.fixed
                bottom = 0.px
                right = 0.px
                marginRight = 2.em
                marginBottom = 2.em
            }

            child(RunController::class) {}
        }
    }
}
