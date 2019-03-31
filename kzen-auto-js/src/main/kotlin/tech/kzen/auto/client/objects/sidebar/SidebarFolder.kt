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
import tech.kzen.lib.common.api.model.DocumentName
import tech.kzen.lib.common.api.model.DocumentPath
import tech.kzen.lib.common.api.model.ObjectReference
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.edit.CreateDocumentCommand
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation
import kotlin.random.Random


class SidebarFolder(
        props: Props
):
        RComponent<SidebarFolder.Props, SidebarFolder.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val documentBase = NotationConventions.mainDocumentPath
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var structure: GraphStructure,
            var selectedDocumentPath: DocumentPath?,
            var documentArchetypes: List<DocumentArchetype>
    ): RProps


    class State(
            var hoverItem: Boolean,
            var hoverOptions: Boolean,
            var optionsOpen: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    private var buttonRef: HTMLButtonElement? = null
    private var mainDocumentsCache: List<DocumentPath>? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun SidebarFolder.State.init(props: SidebarFolder.Props) {
        optionsOpen = false
    }


    override fun componentDidUpdate(
            prevProps: Props,
            prevState: State,
            snapshot: Any
    ) {
        if (props.structure != prevProps.structure) {
            mainDocumentsCache = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun mainDocuments(): List<DocumentPath> {
        if (mainDocumentsCache == null) {
            mainDocumentsCache = props
                    .structure
                    .graphNotation
                    .documents
                    .values
                    .keys
                    .filter { it.startsWith(documentBase) }
        }
        return mainDocumentsCache!!
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onMouseOver(itemOrMenu: Boolean) {
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


    private fun onOptionsToggle() {
        setState {
            optionsOpen = ! optionsOpen
        }
    }


    private fun onOptionsClose() {
        setState {
            optionsOpen = false
            hoverItem = false
            hoverOptions = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun generateDocumentName(
            archetype: DocumentArchetype
    ): DocumentPath {
        val prefix = archetype.name().value

        val suffix = findSuffix(prefix, props.structure)

        return resolve(prefix + suffix)
    }


    private fun findSuffix(
            prefix: String,
            structure: GraphStructure?
    ): String {
        if (structure == null) {
            return "-" + Random.nextInt()
        }
        else {
            for (i in 2 .. 999) {
                val candidateSuffix = "-$i"
                val candidatePath = resolve(prefix + candidateSuffix)

                if (structure.graphNotation.documents.values.containsKey(candidatePath)) {
                    continue
                }

                return candidateSuffix
            }

            return "-" + Random.nextInt()
        }
    }


    private fun resolve(name: String): DocumentPath {
        return documentBase.withName(DocumentName.ofFilenameWithDefaultExtension(name))
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


    private fun onAdd(archetype: DocumentArchetype) {
        onOptionsClose()

        async {
            val newBundleName = generateDocumentName(archetype)
            createDocument(newBundleName, archetype)
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

//                backgroundColor = Color.red
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
        val mainDocuments = mainDocuments()

        if (mainDocuments.isEmpty()) {
            styledDiv {
                css {
                    marginLeft = 2.em
                }
                +"(Empty)"
            }
        }
        else {
            for (documentPath in mainDocuments()) {
                child(SidebarFile::class) {
                    attrs {
                        key = documentPath.asString()

                        structure = props.structure
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
                    onClick = ::onOptionsToggle

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

                onClose = ::onOptionsClose

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
            val archetypeLocation = props.structure.graphNotation.coalesce
                    .locate(ObjectReference.ofName(documentArchetype.name()))

            val icon = (props.structure.graphNotation.coalesce
                    .get(archetypeLocation)
                    .get(SidebarFile.iconAttribute) as ScalarAttributeNotation
                    ).value

            child(MaterialMenuItem::class) {
                attrs {
                    key = documentArchetype.name().value
                    onClick = {
                        onAdd(documentArchetype)
                    }
                }

                child(iconClassForName(icon)) {
                    attrs {
                        style = iconStyle
                    }
                }

                +"New ${documentArchetype.name().value}..."
            }
        }
    }
}