package tech.kzen.auto.client.objects.sidebar

import emotion.react.css
import js.objects.unsafeJso
import mui.material.IconButton
import mui.material.Menu
import mui.material.MenuItem
import mui.system.sx
import react.ChildrenBuilder
import react.Key
import react.RefObject
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.material.iconByName
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.DocumentCreator
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.cqrs.CreateDocumentCommand
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.util.naming.NextAvailableName
import web.cssom.*
import web.html.HTMLElement
import kotlin.js.Date
import kotlin.random.Random


//---------------------------------------------------------------------------------------------------------------------
external interface SidebarFolderProps: react.Props {
    var sidebarModel: SidebarModel
    var selectedDocumentPath: DocumentPath?
}


external interface SidebarFolderState: react.State {
    var hoverItem: Boolean
    var hoverOptions: Boolean
    var optionsOpen: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
class SidebarFolder(
    props: SidebarFolderProps
):
    RComponent<SidebarFolderProps, SidebarFolderState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val documentBaseNesting = NotationConventions.mainDocumentNesting
        private const val menuDanglingTimeout = 300

        val indent = (2).em.minus(4.px)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var menuAnchorRef: RefObject<HTMLElement> = createRef()

    // NB: workaround for open options icon remaining after click with drag away from item
    private var processingOption: Boolean = false
    private var optionCompletedTime: Double? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun SidebarFolderState.init(props: SidebarFolderProps) {
        optionsOpen = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onMouseOver(itemOrMenu: Boolean) {
        if (state.optionsOpen || processingOption) {
            return
        }

        optionCompletedTime?.let {
            val now = Date.now()
            val elapsed = now - it

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
        onOptionsClose()
        optionCompletedTime = Date.now()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun generateDocumentName(
        title: String,
        directory: Boolean
    ): DocumentPath {
        val existing = props.sidebarModel.existingDocumentPaths
        val chosenName = NextAvailableName
            .find(title, separator = "-", range = 1 .. 99) { candidate ->
                resolve(candidate, directory) !in existing
            }
            ?: "$title-${Random.nextInt()}"
        return resolve(chosenName, directory)
    }


    private fun resolve(name: String, directory: Boolean): DocumentPath {
        return DocumentPath(
                DocumentName(name),
                documentBaseNesting,
                directory)
    }


    private suspend fun createDocument(
        documentPath: DocumentPath,
        archetype: SidebarModel.ArchetypeInfo
    ) {
        val newDocument = DocumentCreator.newDocument(archetype.location)

        ClientContext.mirroredGraphStore.apply(
            CreateDocumentCommand(documentPath, newDocument))
    }


    private fun onAdd(archetype: SidebarModel.ArchetypeInfo) {
        processingOption = true
        onOptionsClose()

        async {
            val newBundleName = generateDocumentName(archetype.title, archetype.directory)
            createDocument(newBundleName, archetype)
        }.then {
            optionCompletedTime = Date.now()
            processingOption = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        renderFolderItem()
        renderSubItems()
    }


    private fun ChildrenBuilder.renderFolderItem() {
        div {
            css {
                position = Position.relative
                height = 2.em
                width = 100.pct
            }

            onMouseOver = {
                onMouseOver(true)
            }

            onMouseOut = {
                onMouseOut(true)
            }

            val iconWidth = 24.px

            div {
                css {
                    position = Position.absolute
                    top = 0.px
                    left = 0.px

                    height = iconWidth
                }

                iconByName("FolderOpen") {}
            }

            div {
                css {
                    position = Position.absolute
                    top = 0.px
                    left = iconWidth
                    width = 100.pct.minus(iconWidth)
                    marginLeft = 6.px

                    fontSize = (1.2).em
                }

                +"Project"
            }

            div {
                css {
                    position = Position.absolute
                    top = 0.px
                    right = 0.px
                }
                ref = this@SidebarFolder.menuAnchorRef
                renderOptionsMenu()
            }
        }
    }


    private fun ChildrenBuilder.renderOptionsMenu() {
        span {
            css {
                // NB: blinks in and out without this
                backgroundColor = Color.transparent

                if (!(state.hoverItem || state.hoverOptions)) {
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
                title = "Project options..."
                onClick = { onOptionsOpen() }

                sx {
                    marginTop = (-13).px
                    marginRight = (-16).px
                }

                iconByName("MoreVert") {}
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
        for (archetype in props.sidebarModel.archetypes) {
            MenuItem {
                key = Key(archetype.location.objectPath.name.value)
                onClick = {
                    onAdd(archetype)
                }

                iconByName(archetype.icon) {
                    style = unsafeJso {
                        marginRight = 1.em
                    }
                }

                +"New ${archetype.title}..."
            }
        }
    }


    private fun ChildrenBuilder.renderSubItems() {
        val mainDocuments = props.sidebarModel.mainDocumentPaths

        if (mainDocuments.isEmpty()) {
            div {
                css {
                    marginLeft = indent
                }
                +"(Empty)"
            }
            return
        }

        for (documentPath in mainDocuments) {
            val archetype = props.sidebarModel.archetypeOfDocument[documentPath]
                ?: continue

            SidebarFile::class.react {
                key = Key(documentPath.asString())

                archetypeInfo = archetype
                this.documentPath = documentPath
                selected = (documentPath == props.selectedDocumentPath)
            }
        }
    }
}
