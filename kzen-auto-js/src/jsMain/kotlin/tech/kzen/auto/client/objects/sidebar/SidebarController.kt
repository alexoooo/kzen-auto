package tech.kzen.auto.client.objects.sidebar

import emotion.react.css
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.em


//-----------------------------------------------------------------------------------------------------------------
external interface SidebarControllerProps : react.Props {
    var sidebarModel: SidebarModel?
    var documentPath: DocumentPath?
    var navigationGlobal: NavigationGlobal
    var mirroredGraphStore: MirroredGraphStore
}


external interface SidebarControllerState : State


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

        if (model.mainDocumentPaths.isNotEmpty()) {
            props.navigationGlobal.goto(model.mainDocumentPaths[0])
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val model = props.sidebarModel
            ?: return

        div {
            css {
                paddingTop = 1.em
                paddingRight = 1.em
                paddingBottom = 0.5.em
                paddingLeft = 1.em
            }

            SidebarFolder::class.react {
                sidebarModel = model
                selectedDocumentPath = props.documentPath
                navigationGlobal = props.navigationGlobal
                mirroredGraphStore = props.mirroredGraphStore
            }
        }
    }
}
