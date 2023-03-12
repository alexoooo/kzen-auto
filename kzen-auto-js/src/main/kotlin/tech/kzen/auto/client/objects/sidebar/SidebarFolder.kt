package tech.kzen.auto.client.objects.sidebar

import csstype.*
import emotion.react.css
import js.core.jso
import mui.material.IconButton
import mui.material.Menu
import mui.material.MenuItem
import react.ChildrenBuilder
import react.RefObject
import react.createRef
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.react
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.material.FolderOpenIcon
import tech.kzen.auto.client.wrap.material.MoreVertIcon
import tech.kzen.auto.client.wrap.material.iconClassForName
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.DocumentCreator
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.CreateDocumentCommand
import tech.kzen.lib.common.service.notation.NotationConventions
import web.html.HTMLElement
import kotlin.js.Date
import kotlin.random.Random




//---------------------------------------------------------------------------------------------------------------------
external interface SidebarFolderProps: react.Props {
    var graphStructure: GraphStructure
    var selectedDocumentPath: DocumentPath?
    var archetypeLocations: List<ObjectLocation>
}


external interface SidebarFolderState: react.State {
    var hoverItem: Boolean
    var hoverOptions: Boolean
    var optionsOpen: Boolean
    var mainDocuments: List<DocumentPath>
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
        mainDocuments = mainDocuments(props.graphStructure)
    }


    override fun componentDidUpdate(
            prevProps: SidebarFolderProps,
            prevState: SidebarFolderState,
            snapshot: Any
    ) {
        if (props.graphStructure != prevProps.graphStructure) {
            setState {
                mainDocuments = mainDocuments(props.graphStructure)
            }
        }
    }


    override fun shouldComponentUpdate(nextProps: SidebarFolderProps, nextState: SidebarFolderState): Boolean {
        if (state.hoverItem != nextState.hoverItem ||
                state.hoverOptions != nextState.hoverOptions ||
                state.optionsOpen != nextState.optionsOpen ||
                state.mainDocuments != nextState.mainDocuments
        ) {
            return true
        }

        if (props.selectedDocumentPath != nextProps.selectedDocumentPath ||
                props.archetypeLocations != nextProps.archetypeLocations
        ) {
            return true
        }

        return props.graphStructure.graphNotation.documents.values.keys !=
                nextProps.graphStructure.graphNotation.documents.values.keys
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun mainDocuments(structure: GraphStructure): List<DocumentPath> {
        return structure
                .graphNotation
                .documents
                .values
                .keys
                .filter { it.startsWith(documentBaseNesting) }
                .sortedBy { it.asString().lowercase() }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onMouseOver(itemOrMenu: Boolean) {
        if (state.optionsOpen || processingOption) {
//            console.log("^^^ onMouseOver hoverItem - skip due to", state.optionsOpen, processingOption)
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
//            console.log("^^^ onMouseOver hoverItem")
            setState {
                hoverItem = true
            }
        }
        else {
//            console.log("^^^ onMouseOver hoverOptions")
            setState {
                hoverOptions = true
            }
        }
    }


    private fun onMouseOut(itemOrMenu: Boolean) {
        if (itemOrMenu) {
//            console.log("^^^ onMouseOut hoverItem")
            setState {
                hoverItem = false
            }
        }
        else {
//            console.log("^^^ onMouseOut hoverOptions")
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
//        console.log("^^^^^^ onOptionsClose")
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
    private fun generateDocumentName(
            title: String,
            directory: Boolean
    ): DocumentPath {
        val suffix = findSuffix(title, props.graphStructure, directory)
        return resolve(title + suffix, directory)
    }


    private fun findSuffix(
            prefix: String,
            structure: GraphStructure?,
            directory: Boolean
    ): String {
        if (structure == null) {
            return "-" + Random.nextInt()
        }
        else {
            if (testSuffix(structure, prefix, "", directory)) {
                return ""
            }

            for (i in 1 .. 99) {
                val candidateSuffix = "-$i"
                if (! testSuffix(structure, prefix, candidateSuffix, directory)) {
                    continue
                }

                return candidateSuffix
            }

            return "-" + Random.nextInt()
        }
    }


    private fun testSuffix(
            structure: GraphStructure,
            prefix: String,
            candidateSuffix: String,
            directory: Boolean
    ): Boolean {
        val candidatePath = resolve(prefix + candidateSuffix, directory)
        return candidatePath !in structure.graphNotation.documents.values
    }


    private fun resolve(name: String, directory: Boolean): DocumentPath {
        return DocumentPath(
                DocumentName(name),
                documentBaseNesting,
                directory)
    }


    private suspend fun createDocument(
        documentPath: DocumentPath,
        archetypeLocation: ObjectLocation
    ) {
//        val directoryAttribute = props.graphStructure.graphNotation.transitiveAttribute(
//                archetypeLocation, AutoConventions.directoryAttributePath
//        ) as? ScalarAttributeNotation
//
//        val directory = directoryAttribute?.asBoolean() ?: false

        val newDocument = DocumentCreator.newDocument(archetypeLocation)
//        console.log("^^^^^ createDocument - creating", newDocument)

        ClientContext.mirroredGraphStore.apply(
                CreateDocumentCommand(documentPath, newDocument))

//        console.log("^^^^^ createDocument - created", newDocument)
    }


    private fun onAdd(
        archetypeLocation: ObjectLocation,
        title: String
    ) {
        processingOption = true
        onOptionsClose()

        val directoryAttribute = props.graphStructure.graphNotation.firstAttribute(
                archetypeLocation, AutoConventions.directoryAttributePath
        ) as? ScalarAttributeNotation

        val directory = directoryAttribute?.asBoolean() ?: false

        async {
            val newBundleName = generateDocumentName(title, directory)
            createDocument(newBundleName, archetypeLocation)
        }.then {
//            console.log("Setting processingOption = false")
            optionCompletedTime = Date.now()
            processingOption = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
//        +"[Folder]"
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

                FolderOpenIcon::class.react {}
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
                title = "Project options..."
                onClick = { onOptionsOpen() }

                css {
                    marginTop = (-13).px
                    marginRight = (-16).px
                }

                MoreVertIcon::class.react {}
            }
        }

        Menu {
            open = state.optionsOpen
            onClose = ::onOptionsCancel

            anchorEl =
                if (menuAnchorRef.current != null) {
                    { _ -> menuAnchorRef.current!! }
                }
                else {
                    null
                }

            renderMenuItems()
        }
    }


    private fun ChildrenBuilder.renderMenuItems() {
        for (archetypeLocation in props.archetypeLocations) {
            val icon = props
                    .graphStructure
                    .graphNotation
                    .coalesce[archetypeLocation]!!
                    .get(AutoConventions.iconAttributePath)
                    ?.asString()
                    ?: ""

            val title = props
                    .graphStructure
                    .graphNotation
                    .coalesce[archetypeLocation]!!
                    .get(AutoConventions.titleAttributePath)
                    ?.asString()
                    ?: archetypeLocation.objectPath.name.value

            MenuItem {
                key = archetypeLocation.objectPath.name.value
                onClick = {
                    onAdd(archetypeLocation, title)
                }

                iconClassForName(icon).react {
                    style = jso {
                        marginRight = 1.em
                    }
                }

                +"New $title..."
            }
        }
    }


    private fun ChildrenBuilder.renderSubItems() {
        val mainDocuments = state.mainDocuments

        if (mainDocuments.isEmpty()) {
            div {
                css {
                    marginLeft = SidebarFolder.indent
                }
                +"(Empty)"
            }
        }
        else {
            for (documentPath in mainDocuments) {
                SidebarFile::class.react {
                    key = documentPath.asString()

                    structure = props.graphStructure
                    this.documentPath = documentPath
                    selected = (documentPath == props.selectedDocumentPath)
                }
            }
        }
    }
}