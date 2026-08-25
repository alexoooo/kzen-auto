package tech.kzen.auto.client.objects.document.job.display

import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore


external interface FileSourceWorkerDisplayProps: WorkerDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper
    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}


/**
 * The ordinary Worker card with its inline file selection promoted above a collapsed advanced-configuration section.
 *
 * The selection remains an attribute editor selected by `meta.files.editor`; this display only rehomes that editor,
 * so a plugin can contribute another file-selection editor without changing this component or the generic card.
 */
@Suppress("unused")
class FileSourceWorkerDisplay(
    props: FileSourceWorkerDisplayProps
):
    RPureComponent<FileSourceWorkerDisplayProps, State>(props)
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


    override fun ChildrenBuilder.render() {
        WorkerDisplayDefault::class.react {
            this.attributeEditorManager = props.attributeEditorManager
            this.attributeViewManager = props.attributeViewManager
            this.clientStateGlobal = props.clientStateGlobal
            this.mirroredGraphStore = props.mirroredGraphStore
            this.common = props.common
            hiddenAttributes = setOf(filesAttributeName)
            attributeDisclosure = "Advanced"
            bodyBefore = { bodyBuilder -> bodyBuilder.renderFileSelection() }
            bodyExtra = null
        }
    }


    private fun ChildrenBuilder.renderFileSelection() {
        props.attributeEditorManager.child(this) {
            objectLocation = props.common.objectLocation
            attributeName = filesAttributeName
        }
    }
}
