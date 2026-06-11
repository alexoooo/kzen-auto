package tech.kzen.auto.client.objects.sidebar

import emotion.react.css
import js.objects.unsafeJso
import mui.material.MenuItem
import react.ChildrenBuilder
import react.Key
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.objects.document.DocumentCreator
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.cqrs.CreateDocumentCommand
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.util.naming.NextAvailableName
import web.cssom.*
import kotlin.random.Random


//---------------------------------------------------------------------------------------------------------------------
external interface SidebarFolderProps: react.Props {
    var sidebarModel: SidebarModel
    var selectedDocumentPath: DocumentPath?
    var navigationGlobal: NavigationGlobal
    var mirroredGraphStore: MirroredGraphStore
}


//---------------------------------------------------------------------------------------------------------------------
class SidebarFolder(
    props: SidebarFolderProps
):
    RComponent<SidebarFolderProps, react.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val documentBaseNesting = NotationConventions.mainDocumentNesting

        val indent = (2).em.minus(4.px)
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

        props.mirroredGraphStore.apply(
            CreateDocumentCommand(documentPath, newDocument))
    }


    private fun onAdd(archetype: SidebarModel.ArchetypeInfo, close: () -> Unit) {
        close()

        async {
            val newBundleName = generateDocumentName(archetype.title, archetype.directory)
            createDocument(newBundleName, archetype)
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

                SidebarItemMenu.revealOnHoverSelector {
                    opacity = number(1.0)
                }
            }

            val iconWidth = 24.px

            div {
                css {
                    position = Position.absolute
                    top = 0.px
                    left = 0.px

                    height = iconWidth
                }

                icon("material-symbols:folder-open") {}
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

            SidebarItemMenu::class.react {
                title = "Project options..."
                renderItems = { childrenBuilder, close ->
                    childrenBuilder.renderMenuItems(close)
                }
            }
        }
    }


    private fun ChildrenBuilder.renderMenuItems(close: () -> Unit) {
        for (archetype in props.sidebarModel.archetypes) {
            MenuItem {
                key = Key(archetype.location.objectPath.name.value)
                onClick = {
                    onAdd(archetype, close)
                }

                icon(archetype.icon) {
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
                navigationGlobal = props.navigationGlobal
                mirroredGraphStore = props.mirroredGraphStore
            }
        }
    }
}
