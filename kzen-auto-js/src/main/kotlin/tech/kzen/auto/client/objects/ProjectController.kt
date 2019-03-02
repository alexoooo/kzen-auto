package tech.kzen.auto.client.objects

import kotlinx.coroutines.delay
import kotlinx.css.*
import kotlinx.css.properties.boxShadow
import kotlinx.html.title
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import react.*
import react.dom.div
import styled.*
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.script.RunController
import tech.kzen.auto.client.objects.script.ScriptController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.CommandBus
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.MaterialTypography
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.NotationCommand
import tech.kzen.lib.common.structure.notation.edit.NotationEvent
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import kotlin.browser.window


@Suppress("unused")
class ProjectController(
        props: ProjectController.Props
):
        RComponent<ProjectController.Props, ProjectController.State>(props),
        ModelManager.Observer,
        CommandBus.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val shadowWidth = 12.px
        private val sidebarWidth = 16.em
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var headerElement: HTMLElement? = null


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
//            var actionManager: ActionManager
            var ribbonController: RibbonController.Wrapper
    ): RProps

    class State(
            var structure: GraphStructure?,
            var commandError: String?,

            var headerHeight: Int?
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


    private val handleResize: (Event) -> Unit = { _ ->
//        async {
//            delay(1)

            val height = headerElement?.clientHeight ?: 0
            if (state.headerHeight != height) {
                setState {
                    headerHeight = height
                }
            }
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
//        val height = headerElement?.clientHeight ?: 0
//        setState {
//            headerHeight = height
//        }

        async {
            ClientContext.modelManager.observe(this)
            ClientContext.commandBus.observe(this)
        }

        async {
            delay(1)

            val height = headerElement?.clientHeight ?: 0
            setState {
                headerHeight = height
            }
        }

        window.addEventListener("resize", handleResize)
    }


    override fun componentWillUnmount() {
//        println("ProjectController - Un-subscribed")
        ClientContext.modelManager.unobserve(this)
        ClientContext.commandBus.unobserve(this)

        window.addEventListener("resize", handleResize)
    }


    override fun componentDidUpdate(
            prevProps: ProjectController.Props,
            prevState: ProjectController.State,
            snapshot: Any
    ) {
        val height = headerElement?.clientHeight ?: 0

        if (height != prevState.headerHeight) {
            setState {
                headerHeight = height
            }
        }
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
            val graphNotation = state.structure?.graphNotation
            if (graphNotation == null) {
                +"Loading..."
            }
            else {
                renderHeader(graphNotation)

                renderSidebar()

                renderStage()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderHeader(
            graphNotation: GraphNotation
    ) {
        styledDiv {
            css {
//                position = Position.sticky
                position = Position.fixed
                width = 100.pct
                left = 0.px
                top = 0.px
                zIndex = 999
                boxShadow(Color.gray, 0.px, 0.px, shadowWidth)
            }

            div {
                ref {
                    headerElement = it as? HTMLElement
//                    console.log("^^^^^^^ ref - foo", headerElement, headerElement?.clientHeight)
                }

                child(MaterialTypography::class) {
                    attrs {
                        style = reactStyle {
                            backgroundColor = Color.white
//                            backgroundColor = Color.red

                            paddingTop = 1.em
                            paddingRight = 1.75.em
                            paddingBottom = 1.em
                            paddingLeft = 1.75.em
                        }
                    }

                    styledSpan {
                        css {
                            float = Float.left
                            marginLeft = (-11).px
                            marginTop = (-9).px
                        }

                        renderLogo()
                    }


                    styledSpan {
                        css {
                            marginLeft = 1.em
                        }

                        props.ribbonController.child(this) {
                            attrs {
                                notation = graphNotation
                                path = NotationConventions.mainPath
                            }
                        }
                    }
                }
            }
        }

        // NB: cover up shadow from sidebar
        styledDiv {
            val headerHeight = (state.headerHeight ?: 64).px
            css {
                position = Position.fixed
                width = 100.vw.minus(10.em)
                height = shadowWidth
                left = 10.em
                top = headerHeight.minus(shadowWidth)
                zIndex = 1000
                backgroundColor = Color.white
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
//                    height = 35.px
                    height = 52.px
                }

                attrs {
                    title = "Kzen (home)"
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderSidebar() {
        styledDiv {
            val headerHeight = (state.headerHeight ?: 64).px

            css {
                width = sidebarWidth

                height = 100.vh.minus(headerHeight).plus(shadowWidth)
                position = Position.fixed
                left = 0.px
                top = headerHeight.minus(shadowWidth)

                zIndex = 999
                boxShadow(Color.gray, 0.px, shadowWidth, shadowWidth)
            }

            styledDiv {
                css {
                    backgroundColor = Color.white

                    height = 100.pct

                    borderTopWidth = 1.px
                    borderTopStyle = BorderStyle.solid
                    borderTopColor = Color.lightGray

                    marginTop = shadowWidth
                }

                renderSidebarContent()
            }
        }
    }


    private fun RBuilder.renderSidebarContent() {
        styledDiv {
            css {
                paddingTop = 1.em
                paddingRight = 1.em
                paddingBottom = 1.em
                paddingLeft = 1.em
            }

            +"Side bar..."
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderStage() {
        styledDiv {
            val headerHeight = (state.headerHeight ?: 64).px
            val leftPad = 1.em

            css {
//                display = Display.inlineBlock
                width = 100.pct.minus(sidebarWidth).minus(leftPad)
                minHeight = 100.vh.minus(headerHeight)

                marginTop = headerHeight
                marginLeft = sidebarWidth

                paddingLeft = leftPad
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
