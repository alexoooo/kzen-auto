package tech.kzen.auto.client.objects.sidebar

import csstype.*
import emotion.react.css
import js.core.jso
import mui.material.IconButton
import mui.material.Menu
import mui.material.MenuItem
import mui.system.sx
import react.*
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.DeleteIcon
import tech.kzen.auto.client.wrap.material.EditIcon
import tech.kzen.auto.client.wrap.material.MoreVertIcon
import tech.kzen.auto.client.wrap.material.iconClassForName
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.DeleteDocumentCommand
import web.html.HTMLElement
import kotlin.js.Date




//---------------------------------------------------------------------------------------------------------------------
external interface SidebarFileProps: Props {
    var structure: GraphStructure
    var documentPath: DocumentPath
    var selected: Boolean
}


// TODO: centralize menu logic with SidebarFolder / ActionController
external interface SidebarFileState: State {
    var hoverItem: Boolean
    var hoverOptions: Boolean
    var optionsOpen: Boolean
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
        private const val menuDanglingTimeout = 300

        private val iconWidth = 22.px
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var menuAnchorRef: RefObject<HTMLElement> = createRef()
    private var nameEditorRef: RefObject<DocumentNameEditor> = createRef()

    private var processingOption: Boolean = false
    private var optionCompletedTime: Double? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun SidebarFileState.init(props: SidebarFileProps) {
        optionsOpen = false
        editing = false
        parameters = RequestParams.empty
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        ClientContext.navigationGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        ClientContext.navigationGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(documentPath: DocumentPath?, parameters: RequestParams) {
        setState {
            this.parameters = parameters
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onMouseOver(itemOrMenu: Boolean) {
        if (state.optionsOpen || processingOption) {
//            console.log("^^^ onMouseOver hoverItem - skip due to optionsOpen")
            return
        }

        optionCompletedTime?.let {
            val now = Date.now()
            val elapsed = now - it
//            console.log("^^^ onMouseOver hoverItem - elapsed", elapsed)

            if (elapsed < menuDanglingTimeout) {
                return
            }
            else {
                optionCompletedTime = null
            }
        }

        if (itemOrMenu) {
            setState {
                hoverItem = true
            }
        }
        else {
            setState {
                hoverOptions = true
            }
        }
    }


    private fun onMouseOut(itemOrMenu: Boolean) {
        if (itemOrMenu) {
            setState {
                hoverItem = false
            }
        }
        else {
            setState {
                hoverOptions = false
            }
        }
    }


    private fun onOptionsOpen() {
        setState {
            optionsOpen = true
        }
    }


    private fun onOptionsClose() {
        setState {
            optionsOpen = false
            hoverItem = false
            hoverOptions = false
        }
    }


    private fun onOptionsCancel() {
//        console.log("^^^^^^ onOptionsCancel")
        onOptionsClose()
        optionCompletedTime = Date.now()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRename() {
        performOption {
            nameEditorRef.current?.onEdit()
        }
    }


    private fun onRemove() {
        performOption {
            ClientContext.mirroredGraphStore.apply(DeleteDocumentCommand(props.documentPath))
        }
    }


    private fun performOption(action: suspend () -> Unit) {
        processingOption = true
        onOptionsClose()

        async {
            action.invoke()
        }.then {
            optionCompletedTime = Date.now()
            processingOption = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
//        val documentArchetype = props.structure.graphNotation

        val archetypeLocation = DocumentArchetype
                .archetypeLocation(props.structure.graphNotation, props.documentPath)
                ?: return

        div {
            css {
                position = Position.relative
                height = 2.em
                width = 100.pct.minus(SidebarFolder.indent)
                marginLeft = SidebarFolder.indent
            }

            onMouseOver = {
                onMouseOver(true)
            }
            onMouseOut = {
                onMouseOut(true)
            }

            if (state.editing) {
                renderIconAndName(archetypeLocation)
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

                    renderIconAndName(archetypeLocation)
                }
            }

            div {
                css {
                    position = Position.absolute
                    top = 0.px
                    right = 0.px
                }

                ref = this@SidebarFile.menuAnchorRef

                renderOptionsMenu()
            }
        }
    }


    private fun ChildrenBuilder.renderIconAndName(
            archetypeLocation: ObjectLocation
    ) {
        val icon = (props.structure.graphNotation.coalesce[archetypeLocation]!!
                .get(AutoConventions.iconAttributePath) as ScalarAttributeNotation
                ).value

        div {
            css {
                position = Position.absolute
                top = 0.px
                left = 0.px

                height = iconWidth
            }

            iconClassForName(icon).react {
                title = archetypeLocation.objectPath.name.value
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
    private fun ChildrenBuilder.renderOptionsMenu() {
        span {
            css {
                // NB: blinks in and out without this
                backgroundColor = NamedColor.transparent

                if (! (state.hoverItem || state.hoverOptions)) {
                    display = None.none
                }
            }

            onMouseOver = {
                onMouseOver(false)
            }

            onMouseOut = {
                onMouseOut(false)
            }

            IconButton {
                title = "Options..."
                onClick = { onOptionsOpen() }

                sx {
                    marginTop = (-13).px
                    marginRight = (-16).px
                }

                MoreVertIcon::class.react {}
            }
        }

        Menu {
            open = state.optionsOpen
            onClose = ::onOptionsCancel
            anchorEl = menuAnchorRef.current?.let { { _ -> it } }
            renderMenuItems()
        }
    }


    private fun ChildrenBuilder.renderMenuItems() {
        val iconStyle: CSSProperties = jso {
            marginRight = 1.em
        }

        MenuItem {
            onClick = { onRename() }
            EditIcon::class.react {
                style = iconStyle
            }
            +"Rename"
        }

        MenuItem {
            onClick = { onRemove() }
            DeleteIcon::class.react {
                style = iconStyle
            }
            +"Delete"
        }
    }
}