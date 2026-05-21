package tech.kzen.auto.client.objects.sidebar

import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.Reflect
import web.cssom.em


//-----------------------------------------------------------------------------------------------------------------
external interface SidebarControllerProps : react.Props {
    var archetypeLocations: List<ObjectLocation>
    var graphStructure: GraphStructure?
    var documentPath: DocumentPath?
}


external interface SidebarControllerState : State


//-----------------------------------------------------------------------------------------------------------------
// State (graphStructure, documentPath) is owned by ProjectController and passed in as props.
// Subscribing to global stores here caused the fiber to retain pending-work bits after initial
// setState commits, which made React revisit this component on every hover-driven commit in
// CustomController (RPureComponent bailed at SCU, but the visits still showed in DevTools).
class SidebarController(
    props: SidebarControllerProps
):
    RPureComponent<SidebarControllerProps, SidebarControllerState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetypes: List<ObjectLocation>
    ): ReactWrapper<SidebarControllerProps> {
        override fun ChildrenBuilder.child(block: SidebarControllerProps.() -> Unit) {
            SidebarController::class.react {
                archetypeLocations = this@Wrapper.archetypes
                block()
            }
        }
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
        val structure = props.graphStructure
            ?: return

        if (props.documentPath != null) {
            return
        }

        val mainDocuments = AutoConventions.mainDocuments(structure.graphNotation)
        if (mainDocuments.isNotEmpty()) {
            ClientContext.navigationGlobal.goto(mainDocuments[0])
        }
    }


    // Attribute-only Notation mutations produce a new GraphStructure ref but the same
    // document-tree keys; render() depends only on the tree, so we bail in that case to
    // avoid the fiber participating in the commit (which sticks scheduler bookkeeping bits
    // and causes phantom revisits on subsequent hover-driven commits).
    override fun shouldComponentUpdate(
        nextProps: SidebarControllerProps,
        nextState: SidebarControllerState
    ): Boolean {
        console.log("shouldComponentUpdate")
        if (props.documentPath != nextProps.documentPath) return true
        if (props.archetypeLocations !== nextProps.archetypeLocations) return true

        val prev = props.graphStructure
        val next = nextProps.graphStructure
        if (prev === next) return false
        if (prev == null || next == null) return true

        return prev.graphNotation.documents.map.keys != next.graphNotation.documents.map.keys
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val structure = props.graphStructure
            ?: return

        div {
            css {
                paddingTop = 1.em
                paddingRight = 1.em
                paddingBottom = 0.5.em
                paddingLeft = 1.em
            }

            SidebarFolder::class.react {
                this.graphStructure = structure
                selectedDocumentPath = props.documentPath
                archetypeLocations = props.archetypeLocations
            }
        }
    }
}
