package tech.kzen.auto.client.objects.sidebar

import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.Display
import web.cssom.em
import web.cssom.pct
import web.cssom.px


//-----------------------------------------------------------------------------------------------------------------
external interface SidebarControllerProps : react.Props {
    var sidebarModel: SidebarModel?
    var documentPath: DocumentPath?
    var executingDepths: Map<DocumentPath, Int>
    var tracedDocuments: Set<DocumentPath>
    var navigationGlobal: NavigationGlobal
    var mirroredGraphStore: MirroredGraphStore

    // whole-sidebar collapse, owned by ProjectController (which also drives the layout width)
    var collapsed: Boolean
    var onToggleCollapsed: () -> Unit
}


external interface SidebarControllerState : State {
    // the document/folder currently being dragged (move source), shared with every drop target in the tree
    var dragSourcePath: DocumentPath?
}


//-----------------------------------------------------------------------------------------------------------------
// SidebarModel is projected by ProjectController via SidebarModel.Builder, which preserves
// reference identity across attribute-only Notation mutations. That lets RPureComponent's
// default shallow SCU bail without any custom override here.
class SidebarController(
    props: SidebarControllerProps
):
    RPureComponent<SidebarControllerProps, SidebarControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    // Stable instance references so SidebarDocument (RPureComponent) keeps bailing out — a fresh callback per
    // render would defeat its shallow prop compare. The shared dragSourcePath lives in state (not in each row)
    // so a drop target can validate against the live source while the source row stays decoupled from targets.
    private val onDragItemStart: (DocumentPath) -> Unit = { path ->
        setState { dragSourcePath = path }
    }

    private val onDragItemEnd: () -> Unit = {
        setState { dragSourcePath = null }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        @Service private val navigationGlobal: NavigationGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ): ReactWrapper<SidebarControllerProps> {
        override fun ChildrenBuilder.child(block: SidebarControllerProps.() -> Unit) {
            SidebarController::class.react {
                navigationGlobal = this@Wrapper.navigationGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun SidebarControllerState.init(props: SidebarControllerProps) {
        dragSourcePath = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        redirectToFirstMainIfNoSelection()
    }


    override fun componentDidUpdate(
        prevProps: SidebarControllerProps,
        prevState: SidebarControllerState,
        snapshot: Any
    ) {
        redirectToFirstMainIfNoSelection()
    }


    private fun redirectToFirstMainIfNoSelection() {
        val model = props.sidebarModel
            ?: return

        if (props.documentPath != null) {
            return
        }

        val first = model.firstNavigableDocument()
            ?: return

        props.navigationGlobal.goto(first)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val model = props.sidebarModel
            ?: return

        div {
            css {
                paddingTop = 1.em
                paddingBottom = 0.5.em

                // collapsed strip is narrow — trim the horizontal padding so the "Project" affordances fit
                if (props.collapsed) {
                    paddingLeft = 4.px
                    paddingRight = 4.px
                }
                else {
                    paddingLeft = 1.em
                    paddingRight = 0.5.em
                }
            }

            // inline-block shrink-wraps to the widest row (its max-content) while staying at least the visible
            // width, giving every row one shared width — that's what keeps the sticky ⋮ menus glued to the right
            // edge uniformly while scrolling horizontally (see SidebarRow).
            div {
                css {
                    display = Display.inlineBlock
                    minWidth = 100.pct
                }

                SidebarFolder::class.react {
                    node = null
                    depth = 0
                    sidebarModel = model
                    selectedDocumentPath = props.documentPath
                    executingDepths = props.executingDepths
                    tracedDocuments = props.tracedDocuments
                    navigationGlobal = props.navigationGlobal
                    mirroredGraphStore = props.mirroredGraphStore
                    collapsed = props.collapsed
                    onToggleCollapsed = props.onToggleCollapsed

                    dragSourcePath = state.dragSourcePath
                    onDragItemStart = this@SidebarController.onDragItemStart
                    onDragItemEnd = this@SidebarController.onDragItemEnd
                }
            }
        }
    }
}
