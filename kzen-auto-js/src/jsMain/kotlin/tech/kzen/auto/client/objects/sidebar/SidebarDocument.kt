package tech.kzen.auto.client.objects.sidebar

import emotion.react.css
import js.objects.unsafeJso
import mui.material.MenuItem
import react.*
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.cqrs.DeleteDocumentCommand
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface SidebarDocumentProps: Props {
    var depth: Int
    var archetypeInfo: SidebarModel.ArchetypeInfo
    var documentPath: DocumentPath
    var selected: Boolean
    var navigationGlobal: NavigationGlobal
    var mirroredGraphStore: MirroredGraphStore
}


external interface SidebarDocumentState: State {
    var parameters: RequestParams
}


//---------------------------------------------------------------------------------------------------------------------
class SidebarDocument(
    props: SidebarDocumentProps
):
    RPureComponent<SidebarDocumentProps, SidebarDocumentState>(props),
    NavigationGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    private var nameEditorRef: RefObject<DocumentNameEditor> = createRef()


    //-----------------------------------------------------------------------------------------------------------------
    override fun SidebarDocumentState.init(props: SidebarDocumentProps) {
        parameters = RequestParams.empty
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.navigationGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.navigationGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(documentPath: DocumentPath?, parameters: RequestParams) {
        setState {
            this.parameters = parameters
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRename(close: () -> Unit) {
        performOption(close) {
            nameEditorRef.current?.onEdit()
        }
    }


    private fun onRemove(close: () -> Unit) {
        performOption(close) {
            props.mirroredGraphStore.apply(DeleteDocumentCommand(props.documentPath))
        }
    }


    private fun performOption(close: () -> Unit, action: suspend () -> Unit) {
        close()

        async {
            action.invoke()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val archetype = props.archetypeInfo

        sidebarRow(props.depth) {
            // empty leading slot keeps file icons aligned under sibling folders' icons (folders carry a chevron)
            div {
                css {
                    flexShrink = number(0.0)
                    width = SidebarRow.leadingSlot
                }
            }

            // The clickable area is a link; the rename editor floats in a portal (DocumentNameEditor), and its
            // Popover backdrop intercepts clicks while open, so the underlying link never navigates mid-edit.
            a {
                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                    flexGrow = number(1.0)

                    color = Globals.inherit
                    textDecoration = Globals.initial
                    height = 100.pct
                }

                href = NavigationRoute(
                    props.documentPath,
                    state.parameters
                ).toFragment()

                renderIconAndName(archetype)
            }

            SidebarItemMenu::class.react {
                title = "Options..."
                renderItems = { childrenBuilder, close ->
                    childrenBuilder.renderMenuItems(close)
                }
            }
        }
    }


    private fun ChildrenBuilder.renderIconAndName(
        archetype: SidebarModel.ArchetypeInfo
    ) {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                flexShrink = number(0.0)

                width = SidebarRow.iconWidth
                height = SidebarRow.iconWidth
            }

            icon(archetype.icon) {
                title = archetype.location.objectPath.name.value
            }
        }

        div {
            css {
                marginLeft = 6.px
                whiteSpace = WhiteSpace.nowrap
                flexShrink = number(0.0)

                if (props.selected) {
                    fontWeight = FontWeight.bold
                }
            }

            DocumentNameEditor::class.react {
                this.ref = nameEditorRef

                this.documentPath = props.documentPath
                mirroredGraphStore = props.mirroredGraphStore
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderMenuItems(close: () -> Unit) {
        val iconStyle: CSSProperties = unsafeJso {
            marginRight = 1.em
        }

        MenuItem {
            onClick = { onRename(close) }
            icon("material-symbols:edit") {
                style = iconStyle
            }
            +"Rename"
        }

        MenuItem {
            onClick = { onRemove(close) }
            icon("material-symbols:delete") {
                style = iconStyle
            }
            +"Delete"
        }
    }
}
