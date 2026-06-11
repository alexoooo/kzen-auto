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
external interface SidebarFileProps: Props {
    var archetypeInfo: SidebarModel.ArchetypeInfo
    var documentPath: DocumentPath
    var selected: Boolean
    var navigationGlobal: NavigationGlobal
    var mirroredGraphStore: MirroredGraphStore
}


external interface SidebarFileState: State {
    var editing: Boolean
    var parameters: RequestParams
}


//---------------------------------------------------------------------------------------------------------------------
class SidebarFile(
        props: SidebarFileProps
):
        RPureComponent<SidebarFileProps, SidebarFileState>(props),
        NavigationGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val iconWidth = 22.px
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var nameEditorRef: RefObject<DocumentNameEditor> = createRef()


    //-----------------------------------------------------------------------------------------------------------------
    override fun SidebarFileState.init(props: SidebarFileProps) {
        editing = false
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

        div {
            css {
                position = Position.relative
                height = 2.em
                width = 100.pct.minus(SidebarFolder.indent)
                marginLeft = SidebarFolder.indent

                SidebarItemMenu.revealOnHoverSelector {
                    opacity = number(1.0)
                }
            }

            if (state.editing) {
                renderIconAndName(archetype)
            }
            else {
                a {
                    css {
                        color = Globals.inherit
                        textDecoration = Globals.initial
                        width = 100.pct
                        height = 100.pct
                    }

                    href = NavigationRoute(
                        props.documentPath,
                        state.parameters
                    ).toFragment()

                    renderIconAndName(archetype)
                }
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
                position = Position.absolute
                top = 0.px
                left = 0.px

                height = iconWidth
            }

            icon(archetype.icon) {
                title = archetype.location.objectPath.name.value
            }
        }

        div {
            css {
                position = Position.absolute
                top = 0.px
                left = iconWidth
                width = 100.pct.minus(iconWidth)
                marginLeft = 6.px

                if (props.selected) {
                    fontWeight = FontWeight.bold
                }

                height = 2.em
            }

            DocumentNameEditor::class.react {
                this.ref = nameEditorRef

                this.documentPath = props.documentPath
                mirroredGraphStore = props.mirroredGraphStore

                initialEditing = state.editing

                onEditing = {
                    setState {
                        editing = it
                    }
                }
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
