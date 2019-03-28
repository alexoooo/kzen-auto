package tech.kzen.auto.client.objects

import kotlinx.coroutines.delay
import kotlinx.css.*
import kotlinx.css.properties.boxShadow
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import react.*
import react.dom.div
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.StageController
import tech.kzen.auto.client.objects.ribbon.RibbonController
import tech.kzen.auto.client.objects.sidebar.SidebarController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.CommandBus
import tech.kzen.auto.client.service.NavigationManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.common.service.ModelManager
import tech.kzen.lib.common.api.model.DocumentPath
import tech.kzen.lib.common.structure.GraphStructure
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
        CommandBus.Observer,
        NavigationManager.Observer
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
            var ribbonController: RibbonController.Wrapper,
            var sidebarController: SidebarController.Wrapper,
            var stageController: StageController.Wrapper
    ): RProps


    class State(
            var structure: GraphStructure?,
            var commandError: String?,
//            var bundlePath: BundlePath?,

            var headerHeight: Int?
    ): RState


    @Suppress("unused")
    class Wrapper(
            private val ribbonController: RibbonController.Wrapper,
            private val sidebarController: SidebarController.Wrapper,
            private val stageController: StageController.Wrapper
    ): ReactWrapper<ProjectController.Props> {
        override fun child(input: RBuilder, handler: RHandler<ProjectController.Props>): ReactElement {
            return input.child(ProjectController::class) {
                attrs {
                    ribbonController = this@Wrapper.ribbonController
                    sidebarController = this@Wrapper.sidebarController
                    stageController = this@Wrapper.stageController
                }

                handler()
            }
        }
    }


    // TODO: is there a way to directly observe the headerElement height change?
    private val handleResize: (Event?) -> Unit = { _ ->
        val height = headerElement?.clientHeight ?: 0
        if (state.headerHeight != height) {
            setState {
                headerHeight = height
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
            ClientContext.modelManager.observe(this)
            ClientContext.commandBus.observe(this)
            ClientContext.navigationManager.observe(this)
        }

//        async {
//            delay(1)
//
//            val height = headerElement?.clientHeight ?: 0
//            setState {
//                headerHeight = height
//            }
//        }

        window.addEventListener("resize", handleResize)
    }


    override fun componentWillUnmount() {
//        println("ProjectController - Un-subscribed")
        ClientContext.modelManager.unobserve(this)
        ClientContext.commandBus.unobserve(this)
        ClientContext.navigationManager.unobserve(this)

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
    override suspend fun handleModel(projectStructure: GraphStructure, event: NotationEvent?) {
//        println("ProjectController - && handled - " +
//                "${projectStructure.graphNotation.bundles.values[NotationConventions.mainPath]?.objects?.values?.keys}")

        setState {
            structure = projectStructure
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
    override fun handleNavigation(documentPath: DocumentPath?) {
        async {
            // NB: account for possible header resize
            delay(1)
            handleResize.invoke(null)
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

                renderStage(/*graphNotation*/)
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

                props.ribbonController.child(this) {
                    attrs {
                        notation = graphNotation
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

                    height = 100.pct.minus(shadowWidth)
                    overflowY = Overflow.auto

                    borderTopWidth = 1.px
                    borderTopStyle = BorderStyle.solid
                    borderTopColor = Color.lightGray

                    marginTop = shadowWidth
                }

                props.sidebarController.child(this) {}
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderStage(
//            graphNotation: GraphNotation
    ) {
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

            props.stageController.child(this) {}
        }
    }
}
