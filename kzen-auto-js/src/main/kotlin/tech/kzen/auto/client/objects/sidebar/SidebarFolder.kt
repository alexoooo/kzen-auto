package tech.kzen.auto.client.objects.sidebar

import kotlinx.css.*
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseOverFunction
import org.w3c.dom.HTMLButtonElement
import react.*
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.CreateDocumentCommand
import kotlin.js.Date
import kotlin.random.Random


class SidebarFolder(
        props: Props
):
        RPureComponent<SidebarFolder.Props, SidebarFolder.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val documentBase = NotationConventions.mainDocumentPath
        private const val menuDanglingTimeout = 300

        val indent = (2).em.minus(4.px)
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var graphStructure: GraphStructure,
            var selectedDocumentPath: DocumentPath?,
            var documentArchetypes: List<DocumentArchetype>
    ): RProps


    class State(
            var hoverItem: Boolean,
            var hoverOptions: Boolean,
            var optionsOpen: Boolean,
            var mainDocuments: List<DocumentPath>
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    private var buttonRef: HTMLButtonElement? = null
//    private var mainDocumentsCache: List<DocumentPath>? = null

    // NB: workaround for open options icon remaining after click with drag away from item
    private var processingOption: Boolean = false
    private var optionCompletedTime: Double? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        optionsOpen = false
        mainDocuments = mainDocuments(props.graphStructure)
    }


    override fun componentDidUpdate(
            prevProps: Props,
            prevState: State,
            snapshot: Any
    ) {
        if (props.graphStructure != prevProps.graphStructure) {
            setState {
                mainDocuments = mainDocuments(props.graphStructure)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun mainDocuments(structure: GraphStructure): List<DocumentPath> {
        return structure
                .graphNotation
                .documents
                .values
                .keys
                .filter { it.startsWith(documentBase) }
                .sortedBy { it.asString().toLowerCase() }
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
            title: String
    ): DocumentPath {
        val suffix = findSuffix(title, props.graphStructure)
        return resolve(title + suffix)
    }


    private fun findSuffix(
            prefix: String,
            structure: GraphStructure?
    ): String {
        if (structure == null) {
            return "-" + Random.nextInt()
        }
        else {
            if (testSuffix(structure, prefix, "")) {
                return ""
            }

            for (i in 1 .. 99) {
                val candidateSuffix = "-$i"
                if (! testSuffix(structure, prefix, candidateSuffix)) {
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
            candidateSuffix: String
    ): Boolean {
        val candidatePath = resolve(prefix + candidateSuffix)
        return candidatePath !in structure.graphNotation.documents.values
    }


    private fun resolve(name: String): DocumentPath {
        return documentBase.withName(DocumentName.ofYaml(name))
    }


    private suspend fun createDocument(
            documentPath: DocumentPath,
            archetype: DocumentArchetype
    ) {
        val newDocument = archetype.newDocument()
//        console.log("^^^^^ createDocument", newDocument)

        ClientContext.commandBus.apply(
                CreateDocumentCommand(documentPath, newDocument))
    }


    private fun onAdd(
            archetype: DocumentArchetype,
            title: String
    ) {
        processingOption = true
        onOptionsClose()

        async {
            val newBundleName = generateDocumentName(title)
            createDocument(newBundleName, archetype)
        }.then {
//            console.log("Setting processingOption = false")
            optionCompletedTime = Date.now()
            processingOption = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        renderFolderItem()

        renderSubItems()
    }


    private fun RBuilder.renderFolderItem() {
        styledDiv {
            css {
                position = Position.relative
                height = 2.em
                width = 100.pct
            }

            attrs {
                onMouseOverFunction = {
                    onMouseOver(true)
                }

                onMouseOutFunction = {
                    onMouseOut(true)
                }
            }

//            val iconWidth = 22.px
            val iconWidth = 24.px

            styledDiv {
                css {
                    position = Position.absolute
//                    width = 100.pct
                    top = 0.px
                    left = 0.px

                    height = iconWidth
                }

                child(FolderOpenIcon::class) {}
            }

            styledDiv {
                css {
                    position = Position.absolute
//                    width = 100.pct
                    top = 0.px
                    left = iconWidth
                    width = 100.pct.minus(iconWidth)
                    marginLeft = 6.px

                    fontSize = (1.2).em
                }

                +"Project"
            }

            styledDiv {
                css {
                    position = Position.absolute
                    top = 0.px
                    right = 0.px

//                    backgroundColor = Color.blue
                }

                renderOptionsMenu()
            }
        }
    }


    private fun RBuilder.renderSubItems() {
        val mainDocuments = state.mainDocuments

        if (mainDocuments.isEmpty()) {
            styledDiv {
                css {
                    marginLeft = SidebarFolder.indent
                }
                +"(Empty)"
            }
        }
        else {
            for (documentPath in mainDocuments) {
                child(SidebarFile::class) {
                    attrs {
                        key = documentPath.asString()

                        structure = props.graphStructure
                        this.documentPath = documentPath
                        selected = (documentPath == props.selectedDocumentPath)
                    }
                }
            }
        }
    }


    private fun RBuilder.renderOptionsMenu() {
        styledSpan {
            css {
                // NB: blinks in and out without this
                backgroundColor = Color.transparent

                if (! (state.hoverItem || state.hoverOptions)) {
                    display = Display.none
                }
            }

            attrs {
                onMouseOverFunction = {
                    onMouseOver(false)
                }

                onMouseOutFunction = {
                    onMouseOut(false)
                }
            }

            child(MaterialIconButton::class) {
                attrs {
                    title = "Project options..."
                    onClick = ::onOptionsOpen

                    buttonRef = {
                        this@SidebarFolder.buttonRef = it
                    }

                    style = reactStyle {
                        marginTop = (-13).px
                        marginRight = (-16).px
                    }
                }

                child(MoreVertIcon::class) {}
            }
        }

        child(MaterialMenu::class) {
            attrs {
                open = state.optionsOpen

                onClose = ::onOptionsCancel

                anchorEl = buttonRef
            }

            renderMenuItems()
        }
    }


    private fun RBuilder.renderMenuItems() {
        val iconStyle = reactStyle {
            marginRight = 1.em
        }

        for (documentArchetype in props.documentArchetypes) {
            val archetypeLocation = props.graphStructure.graphNotation.coalesce
                    .locate(ObjectReference.ofName(documentArchetype.name()))

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
                    ?: documentArchetype.name().value

            child(MaterialMenuItem::class) {
                attrs {
                    key = documentArchetype.name().value
                    onClick = {
                        onAdd(documentArchetype, title)
                    }
                }

                child(iconClassForName(icon)) {
                    attrs {
                        style = iconStyle
                    }
                }

                +"New $title..."
            }
        }
    }
}