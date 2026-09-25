package tech.kzen.auto.client.objects.document.common.file


import emotion.react.css
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.IconButton
import mui.material.Size
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.common.edit.TextAttributeEditor
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface DirectoryEditorProps: AttributeEditorProps {
    var restClient: ClientRestApi
}


external interface DirectoryEditorState: State {
    var value: String?
    var label: String?

    var open: Boolean

    // The folder being shown, as the server resolved it (a relative value reads as the place it resolves to)
    var browseDirectory: DataLocation?
    var folders: List<DataLocationInfo>?
    var loading: Boolean
    var error: String?
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * A directory path: typed like any text attribute, or chosen by walking folders in place under the field — the
 * same listing the file browser reads ([ClientRestApi.listFiles]), showing folders only. The walk starts where the
 * stored value resolves, and "Use this folder" stores that folder's absolute path. Inline rather than a dialog,
 * like [tech.kzen.auto.client.objects.document.job.edit.FileSelectionEditor]'s browser. Wired via
 * `editor: DirectoryEditor`.
 */
@Suppress("unused")
class DirectoryEditor(
    props: DirectoryEditorProps
):
    ObjectScopedComponent<DirectoryEditorProps, DirectoryEditorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val restClient: ClientRestApi
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            DirectoryEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                restClient = this@Wrapper.restClient
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var mounted = false

    // Only the latest listing request may land: a slow reply for a folder already left is dropped
    private var listingEpoch = 0


    //-----------------------------------------------------------------------------------------------------------------
    override fun DirectoryEditorState.init(props: DirectoryEditorProps) {
        open = false
        loading = false
    }


    override fun componentDidMount() {
        super.componentDidMount()
        mounted = true
    }


    override fun componentWillUnmount() {
        super.componentWillUnmount()
        mounted = false
    }


    override fun onClientState(clientState: ClientState) {
        val graphStructure = clientState.graphStructure()

        val label = CommonEditUtils.declaredLabel(graphStructure, props.objectLocation, props.attributeName)
        val value = (graphStructure
            .graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)
                as? ScalarAttributeNotation)
            ?.value

        if (state.value == value && state.label == label) {
            return
        }

        setState {
            this.value = value
            this.label = label
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onToggle() {
        if (state.open) {
            setState { open = false }
            return
        }
        setState { open = true }
        browse(state.value.orEmpty().ifBlank { "." })
    }


    private fun browse(directory: String) {
        val epoch = ++listingEpoch
        setState {
            loading = true
            error = null
        }
        async {
            val outcome = runCatching { props.restClient.listFiles(directory, "") }
            if (!mounted || epoch != listingEpoch) {
                return@async
            }
            val listing = outcome.getOrNull()
            setState {
                loading = false
                if (listing == null) {
                    error = outcome.exceptionOrNull()?.message ?: "Unable to list $directory"
                }
                else {
                    browseDirectory = listing.directory
                    folders = listing.files.filter { it.directory }.sortedBy { it.name.lowercase() }
                }
            }
        }
    }


    private fun onUse(directory: DataLocation) {
        setState { open = false }
        val chosen = directory.asString()
        if (chosen == state.value) {
            return
        }
        async {
            props.mirroredGraphStore.apply(UpsertAttributeCommand(
                props.objectLocation,
                props.attributeName,
                ScalarAttributeNotation(chosen)))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
            }

            div {
                css {
                    flexGrow = number(1.0)
                }
                TextAttributeEditor::class.react {
                    objectLocation = props.objectLocation
                    attributePath = AttributePath.ofName(props.attributeName)
                    value = state.value ?: ""
                    labelOverride = state.label
                    mirroredGraphStore = props.mirroredGraphStore
                }
            }

            IconButton {
                title = if (state.open) "Hide folders" else "Choose a folder"
                onClick = { onToggle() }
                icon(if (state.open) "material-symbols:folder-open" else "material-symbols:folder") {}
            }
        }

        if (state.open) {
            renderPicker()
        }
    }


    private fun ChildrenBuilder.renderPicker() {
        val directory = state.browseDirectory
        div {
            css {
                marginTop = 0.25.em
                marginBottom = 0.5.em
            }

            div {
                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                    gap = 0.5.em
                    marginBottom = 0.25.em
                }

                IconButton {
                    size = Size.small
                    title = "Parent folder"
                    disabled = directory?.parent() == null
                    onClick = { directory?.parent()?.let { browse(it.asString()) } }
                    icon("material-symbols:arrow-upward") {}
                }

                div {
                    css {
                        flexGrow = number(1.0)
                        fontFamily = FontFamily.monospace
                        fontSize = 0.85.em
                        overflowWrap = OverflowWrap.anywhere
                    }
                    +(directory?.asString() ?: "…")
                }

                Button {
                    variant = ButtonVariant.outlined
                    size = Size.small
                    disabled = directory == null || state.loading
                    onClick = { directory?.let(::onUse) }
                    +"Use this folder"
                }
            }

            val error = state.error
            if (error != null) {
                div {
                    css {
                        color = NamedColor.red
                        fontSize = 0.85.em
                    }
                    +"Error: $error"
                }
            }

            renderFolders()
        }
    }


    private fun ChildrenBuilder.renderFolders() {
        val folders = state.folders
            ?: return
        if (folders.isEmpty()) {
            div {
                css {
                    fontSize = 0.85.em
                    fontStyle = FontStyle.italic
                    color = Color("rgba(0, 0, 0, 0.6)")
                }
                +"No subfolders"
            }
            return
        }

        div {
            css { fileTableScrollFrame(30.vh) }
            table {
                css { fileTableGrid() }
                tbody {
                    css { if (state.loading) opacity = number(0.5) }
                    for (folder in folders) {
                        tr {
                            key = Key(folder.path.asString())
                            css {
                                cursor = Cursor.pointer
                                hover { backgroundColor = FileTableColors.hoverRow }
                            }
                            onClick = { browse(folder.path.asString()) }
                            td {
                                css { width = 2.em }
                                icon("material-symbols:folder") {}
                            }
                            td { +folder.name }
                        }
                    }
                }
            }
        }
    }
}
