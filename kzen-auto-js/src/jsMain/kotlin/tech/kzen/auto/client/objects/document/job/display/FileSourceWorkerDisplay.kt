package tech.kzen.auto.client.objects.document.job.display

import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.objects.document.common.file.FileBrowserToggleChannel
import tech.kzen.auto.client.objects.document.common.file.FileBrowserToggleKey
import tech.kzen.auto.client.objects.document.common.file.fileBrowserToggle
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore


external interface FileSourceWorkerDisplayProps: WorkerDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper
    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}


external interface FileSourceWorkerDisplayState: State {
    // Whether anything is selected, read from notation rather than from the editor: an empty selection pins the
    // browser open, so there is nothing for a toggle to do and it is not drawn.
    var selectionEmpty: Boolean

    // Mirrors the shared channel's openness for this card. The channel owns the fact; this is what React compares
    // to decide there is anything to redraw (RPureComponent skips an update whose state shallow-compares equal).
    var browserOpen: Boolean
}


/**
 * The ordinary Worker card with its inline file selection promoted above a collapsed advanced-configuration section,
 * and the browser's show/hide moved up into the card's title bar.
 *
 * The selection remains an attribute editor selected by `meta.files.editor`; this display only rehomes that editor,
 * so a plugin can contribute another file-selection editor without changing this component or the generic card.
 * The toggle is hoisted the same way — through the shared
 * [FileBrowserToggleChannel], never by reaching into the editor — which is why a card whose header does not claim
 * one still works, with the editor drawing its own.
 *
 * Report's Input panel is the precedent: the browser is a mode the panel is in, so the control that changes it
 * belongs beside the panel's name, not in the body it rearranges.
 */
@Suppress("unused")
class FileSourceWorkerDisplay(
    props: FileSourceWorkerDisplayProps
):
    RPureComponent<FileSourceWorkerDisplayProps, FileSourceWorkerDisplayState>(props),
    ClientStateGlobal.DocumentScopedObserver,
    FileBrowserToggleChannel.Observer
{
    companion object {
        private val filesAttributeName = AttributeName("files")
    }


    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        private val attributeViewManager: AttributeViewManager.Wrapper,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        WorkerDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: WorkerDisplayProps.() -> Unit) {
            FileSourceWorkerDisplay::class.react {
                this.attributeEditorManager = this@Wrapper.attributeEditorManager
                this.attributeViewManager = this@Wrapper.attributeViewManager
                this.clientStateGlobal = this@Wrapper.clientStateGlobal
                this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    init {
        installContextType(DocumentBridgeContext)
    }


    override fun FileSourceWorkerDisplayState.init(props: FileSourceWorkerDisplayProps) {
        selectionEmpty = true
        browserOpen = false
    }


    private var toggleChannel: FileBrowserToggleChannel? = null


    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
        toggleChannel?.observe(props.common.objectLocation, this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
        toggleChannel?.unobserve(props.common.objectLocation, this)
        toggleChannel?.unhost(props.common.objectLocation)
    }


    override fun onClientState(clientState: ClientState) {
        val selection = clientState
            .graphStructure()
            .graphNotation
            .firstAttribute(props.common.objectLocation, filesAttributeName)
            as? ListAttributeNotation

        // Value-guarded: a Worker card re-renders on every progress publish, and the selection changes rarely.
        val selectionEmpty = selection == null || selection.values.isEmpty()
        if (state.selectionEmpty != selectionEmpty) {
            setState {
                this.selectionEmpty = selectionEmpty
            }
        }
    }


    override fun onFileBrowserToggled(objectLocation: ObjectLocation) {
        val browserOpen = toggleChannel?.isOpen(objectLocation) ?: false
        setState {
            this.browserOpen = browserOpen
        }
    }


    override fun ChildrenBuilder.render() {
        // Claiming the header in render, not on mount: React mounts children before their parent, so a claim made
        // in componentDidMount would arrive after the editor below had already decided to draw its own toggle.
        // An idempotent write during render, mirroring how JobController provides its stores.
        val toggleChannel = contextValue<DocumentBridge?>()?.channel(FileBrowserToggleKey)
        toggleChannel?.host(props.common.objectLocation)
        this@FileSourceWorkerDisplay.toggleChannel = toggleChannel

        WorkerDisplayDefault::class.react {
            this.attributeEditorManager = props.attributeEditorManager
            this.attributeViewManager = props.attributeViewManager
            this.clientStateGlobal = props.clientStateGlobal
            this.mirroredGraphStore = props.mirroredGraphStore
            this.common = props.common
            hiddenAttributes = setOf(filesAttributeName)
            attributeDisclosure = "Advanced"
            headerRight = toggleChannel?.let { channel -> { it: ChildrenBuilder -> it.renderToggle(channel) } }
            bodyBefore = { bodyBuilder -> bodyBuilder.renderFileSelection() }
            bodyExtra = null
        }
    }


    private fun ChildrenBuilder.renderToggle(channel: FileBrowserToggleChannel) {
        if (state.selectionEmpty) {
            return
        }

        fileBrowserToggle(state.browserOpen) {
            channel.toggle(props.common.objectLocation)
        }
    }


    private fun ChildrenBuilder.renderFileSelection() {
        props.attributeEditorManager.child(this) {
            objectLocation = props.common.objectLocation
            attributeName = filesAttributeName
        }
    }
}
