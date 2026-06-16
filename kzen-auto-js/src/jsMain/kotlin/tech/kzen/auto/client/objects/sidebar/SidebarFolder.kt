package tech.kzen.auto.client.objects.sidebar

import emotion.react.css
import js.objects.unsafeJso
import kotlinx.browser.window
import mui.material.Divider
import mui.material.IconButton
import mui.material.MenuItem
import mui.system.sx
import react.CSSProperties
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.DocumentCreator
import tech.kzen.lib.common.model.document.DocumentForm
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.cqrs.CreateDocumentCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.CreateFolderCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.DeleteFolderCommand
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.util.naming.NextAvailableName
import web.cssom.*
import kotlin.random.Random


//---------------------------------------------------------------------------------------------------------------------
external interface SidebarFolderProps: react.Props {
    // null = synthetic root "Project" folder (content nesting = main/); non-null = a nested pure folder
    var node: SidebarModel.SidebarFolderNode?
    var depth: Int

    var sidebarModel: SidebarModel
    var selectedDocumentPath: DocumentPath?
    var navigationGlobal: NavigationGlobal
    var mirroredGraphStore: MirroredGraphStore

    // root-only: the whole-sidebar collapse toggle (the "Project" row's button)
    var collapsed: Boolean
    var onToggleCollapsed: (() -> Unit)?
}


external interface SidebarFolderState: State {
    // per-folder subtree expansion (nested folders only; the root uses the `collapsed` prop instead)
    var expanded: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
// Renders one folder and its subtree: the synthetic root "Project" folder when `node` is null, otherwise a nested
// pure folder. Only folders are expandable (chevron); every document — including directory-documents like Feature
// — is a leaf rendered by SidebarFile. "New ..." creates under this folder's content nesting.
class SidebarFolder(
    props: SidebarFolderProps
):
    RComponent<SidebarFolderProps, SidebarFolderState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun SidebarFolderState.init(props: SidebarFolderProps) {
        expanded = true
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun isRoot(): Boolean =
        props.node == null


    private fun contentNesting(): DocumentNesting =
        props.node?.contentNesting ?: NotationConventions.mainDocumentNesting


    private fun children(): List<SidebarModel.SidebarNode> =
        props.node?.children ?: props.sidebarModel.rootChildren


    private fun siblingNames(): Set<String> =
        children().mapTo(mutableSetOf()) { child ->
            when (child) {
                is SidebarModel.SidebarFileNode -> child.path.name.value
                is SidebarModel.SidebarFolderNode -> child.name
            }
        }


    //-----------------------------------------------------------------------------------------------------------------
    private fun toggleExpanded() {
        // NB: setState's lambda is write-only here (see wrap/React.kt) — compute the next value outside it
        val next = !state.expanded
        setState {
            expanded = next
        }
    }


    private fun ensureExpanded() {
        if (!isRoot() && !state.expanded) {
            setState {
                expanded = true
            }
        }
    }


    private fun nextAvailableName(base: String): DocumentName {
        val existingNames = siblingNames()
        val chosen = NextAvailableName
            .find(base, separator = "-", range = 1 .. 99) { candidate ->
                candidate !in existingNames
            }
            ?: "$base-${Random.nextInt()}"
        return DocumentName(chosen)
    }


    private fun onAddDocument(archetype: SidebarModel.ArchetypeInfo, close: () -> Unit) {
        close()
        ensureExpanded()

        async {
            val name = nextAvailableName(archetype.title)
            val documentPath = DocumentPath(name, contentNesting(), archetype.directory)
            val newDocument = DocumentCreator.newDocument(archetype.location)
            props.mirroredGraphStore.apply(
                CreateDocumentCommand(documentPath, newDocument))
        }
    }


    private fun onAddFolder(close: () -> Unit) {
        close()
        ensureExpanded()

        async {
            val name = nextAvailableName("Folder")
            val folderPath = DocumentPath(name, contentNesting(), DocumentForm.Folder)
            props.mirroredGraphStore.apply(
                CreateFolderCommand(folderPath))
        }
    }


    private fun onRemoveFolder(node: SidebarModel.SidebarFolderNode, close: () -> Unit) {
        close()

        val nestedCount = props.sidebarModel.existingDocumentPaths.count { path ->
            path.nesting.startsWith(node.contentNesting)
        }
        if (nestedCount > 0) {
            val confirmed = window.confirm(
                "Delete folder \"${node.name}\" and $nestedCount item(s) inside it?")
            if (!confirmed) {
                return
            }
        }

        // the server cascades: removes the folder directory plus everything nested under it
        async {
            props.mirroredGraphStore.apply(
                DeleteFolderCommand(node.folderPath))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        if (isRoot() && props.collapsed) {
            renderCollapsedRoot()
            return
        }

        renderHeaderRow()

        val showChildren = isRoot() || state.expanded
        if (showChildren) {
            renderChildren()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderCollapsedRoot() {
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = AlignItems.center
            }

            IconButton {
                title = "Expand sidebar"
                onClick = { props.onToggleCollapsed?.invoke() }
                sx {
                    padding = 2.px
                }
                icon("material-symbols:chevron-right") {}
            }

            icon("material-symbols:folder-open") {}
        }
    }


    private fun ChildrenBuilder.renderHeaderRow() {
        val node = props.node

        sidebarRow(props.depth) {
            if (node == null) {
                renderRootHeaderContent()
            }
            else {
                renderFolderHeaderContent(node)
            }
        }
    }


    private fun ChildrenBuilder.renderRootHeaderContent() {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                flexShrink = number(0.0)
                width = 24.px
                height = 24.px
            }
            icon("material-symbols:folder-open") {}
        }

        div {
            css {
                flexGrow = number(1.0)
                marginLeft = 6.px
                whiteSpace = WhiteSpace.nowrap
                fontSize = (1.2).em
            }
            +"Project"
        }

        // collapse button rides in the menu's sticky zone (left of the ⋮) so horizontal scroll never pushes it
        // off-screen; it's opacity-gated until the sidebar is hovered (reveal rule on the scroll container in
        // ProjectController)
        SidebarItemMenu::class.react {
            title = "Project options..."
            leadingContent = { childrenBuilder ->
                childrenBuilder.renderCollapseButton()
            }
            renderItems = { childrenBuilder, close ->
                childrenBuilder.renderCreateItems(close)
            }
        }
    }


    private fun ChildrenBuilder.renderCollapseButton() {
        span {
            asDynamic()["data-collapse-button"] = ""
            css {
                display = Display.flex
                alignItems = AlignItems.center
                opacity = number(0.0)
            }

            IconButton {
                title = "Collapse sidebar"
                onClick = { props.onToggleCollapsed?.invoke() }
                sx {
                    padding = 2.px
                }
                icon("material-symbols:chevron-left") {}
            }
        }
    }


    private fun ChildrenBuilder.renderFolderHeaderContent(node: SidebarModel.SidebarFolderNode) {
        // expand/collapse chevron — also the leading slot that aligns this row's icon with sibling files' icons
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                flexShrink = number(0.0)
                width = SidebarRow.leadingSlot
                height = 100.pct
                cursor = Cursor.pointer
            }
            onClick = { toggleExpanded() }
            icon(if (state.expanded) "material-symbols:expand-more" else "material-symbols:chevron-right") {}
        }

        // a folder isn't navigated to — clicking its name toggles the subtree
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                flexGrow = number(1.0)
                height = 100.pct
                cursor = Cursor.pointer
            }
            onClick = { toggleExpanded() }

            div {
                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                    flexShrink = number(0.0)
                    width = SidebarRow.iconWidth
                    height = SidebarRow.iconWidth
                }
                icon("material-symbols:folder") {}
            }

            div {
                css {
                    marginLeft = 6.px
                    whiteSpace = WhiteSpace.nowrap
                    flexShrink = number(0.0)
                }
                +node.name
            }
        }

        SidebarItemMenu::class.react {
            title = "Folder options..."
            renderItems = { childrenBuilder, close ->
                childrenBuilder.renderFolderMenuItems(node, close)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderChildren() {
        val children = children()
        val childDepth = props.depth + 1

        if (children.isEmpty()) {
            if (isRoot()) {
                div {
                    css {
                        paddingLeft = SidebarRow.indent(childDepth)
                    }
                    +"(Empty)"
                }
            }
            return
        }

        // NB: children are rendered as flat siblings (no width-shrinking wrapper) so all rows share one width;
        //     indentation is the per-row depth pad (see SidebarRow).
        for (child in children) {
            when (child) {
                is SidebarModel.SidebarFileNode ->
                    SidebarFile::class.react {
                        key = Key(child.path.asString())

                        depth = childDepth
                        archetypeInfo = child.archetype
                        documentPath = child.path
                        selected = (child.path == props.selectedDocumentPath)
                        navigationGlobal = props.navigationGlobal
                        mirroredGraphStore = props.mirroredGraphStore
                    }

                is SidebarModel.SidebarFolderNode ->
                    SidebarFolder::class.react {
                        key = Key(child.folderPath.asString())

                        node = child
                        depth = childDepth
                        sidebarModel = props.sidebarModel
                        selectedDocumentPath = props.selectedDocumentPath
                        navigationGlobal = props.navigationGlobal
                        mirroredGraphStore = props.mirroredGraphStore
                        collapsed = false
                        onToggleCollapsed = null
                    }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderCreateItems(close: () -> Unit) {
        val iconStyle: CSSProperties = unsafeJso {
            marginRight = 1.em
        }

        MenuItem {
            onClick = { onAddFolder(close) }
            icon("material-symbols:create-new-folder") {
                style = iconStyle
            }
            +"New Folder..."
        }

        for (archetype in props.sidebarModel.archetypes) {
            MenuItem {
                key = Key(archetype.location.objectPath.name.value)
                onClick = {
                    onAddDocument(archetype, close)
                }

                icon(archetype.icon) {
                    style = iconStyle
                }

                +"New ${archetype.title}..."
            }
        }
    }


    private fun ChildrenBuilder.renderFolderMenuItems(
        node: SidebarModel.SidebarFolderNode,
        close: () -> Unit
    ) {
        renderCreateItems(close)

        Divider {}

        MenuItem {
            onClick = { onRemoveFolder(node, close) }
            icon("material-symbols:delete") {
                style = unsafeJso {
                    marginRight = 1.em
                }
            }
            +"Delete"
        }
    }
}
