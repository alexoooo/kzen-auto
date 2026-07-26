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

    // folders whose children are shown, keyed by the folder's own path; absent = collapsed, so "every folder
    // starts collapsed" is just the empty initial set. Lifted here (rather than per SidebarFolder) because
    // revealing a selection has to expand a whole ancestor chain at once.
    var expandedFolderPaths: Set<DocumentPath>

    // the selection whose ancestors have already been revealed — makes the reveal one-shot per navigation, so a
    // folder the user collapses by hand stays collapsed across unrelated re-renders
    var revealedSelection: DocumentPath?
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

    // NB: setState's lambda is write-only here (see wrap/React.kt) — compute the next set outside it
    private val onToggleFolder: (DocumentPath) -> Unit = { folderPath ->
        val next =
            if (folderPath in state.expandedFolderPaths) {
                state.expandedFolderPaths - folderPath
            }
            else {
                state.expandedFolderPaths + folderPath
            }
        setState { expandedFolderPaths = next }
    }

    private val onExpandFolder: (DocumentPath) -> Unit = { folderPath ->
        if (folderPath !in state.expandedFolderPaths) {
            val next = state.expandedFolderPaths + folderPath
            setState { expandedFolderPaths = next }
        }
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
        expandedFolderPaths = emptySet()
        revealedSelection = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        redirectToFirstMainIfNoSelection()
        revealSelection()
    }


    override fun componentDidUpdate(
        prevProps: SidebarControllerProps,
        prevState: SidebarControllerState,
        snapshot: Any
    ) {
        redirectToFirstMainIfNoSelection()
        revealSelection()
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


    // Expand the folders leading to the selected document, leaving folders the user opened by hand alone.
    // The model can arrive after the selection does, hence the early return without recording revealedSelection.
    private fun revealSelection() {
        val model = props.sidebarModel
            ?: return

        val selected = props.documentPath
            ?: return

        if (state.revealedSelection == selected) {
            return
        }

        val nextExpanded = state.expandedFolderPaths + model.ancestorFolderPaths(selected)
        setState {
            expandedFolderPaths = nextExpanded
            revealedSelection = selected
        }
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

                    expandedFolderPaths = state.expandedFolderPaths
                    onToggleFolder = this@SidebarController.onToggleFolder
                    onExpandFolder = this@SidebarController.onExpandFolder

                    dragSourcePath = state.dragSourcePath
                    onDragItemStart = this@SidebarController.onDragItemStart
                    onDragItemEnd = this@SidebarController.onDragItemEnd
                }
            }
        }
    }
}
