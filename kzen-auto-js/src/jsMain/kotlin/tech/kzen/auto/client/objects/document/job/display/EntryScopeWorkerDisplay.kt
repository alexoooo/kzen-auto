package tech.kzen.auto.client.objects.document.job.display

import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.objects.document.job.edit.ScopeBodyEditor
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore


external interface EntryScopeWorkerDisplayProps: WorkerDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper
    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}


/**
 * The ordinary Worker card for an `EntryScope` (content streaming spike), with its nested `body` (the
 * `ScopeBodyWorker` instances re-entered per entry) edited in place by [ScopeBodyEditor] below the scope's own
 * attributes. The body is hidden from the generic per-attribute loop, which has no editor for a list of nested
 * objects, and rehomed here rather than registered as an `editor:` because the body editor needs the
 * [AttributeEditorManager] for each item's attributes, and an AttributeEditor holding the manager would close a
 * reference cycle through the manager's autowired editor list.
 */
@Suppress("unused")
class EntryScopeWorkerDisplay(
    props: EntryScopeWorkerDisplayProps
):
    RPureComponent<EntryScopeWorkerDisplayProps, State>(props)
{
    companion object {
        private val bodyAttributeName = AttributeName("body")
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
            EntryScopeWorkerDisplay::class.react {
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
            hiddenAttributes = setOf(bodyAttributeName)
            attributeDisclosure = null
            headerRight = null
            bodyBefore = null
            bodyExtra = { bodyBuilder -> bodyBuilder.renderBody() }
        }
    }


    private fun ChildrenBuilder.renderBody() {
        ScopeBodyEditor::class.react {
            objectLocation = props.common.objectLocation
            attributeName = bodyAttributeName
            attributeEditorManager = props.attributeEditorManager
            clientStateGlobal = props.clientStateGlobal
            mirroredGraphStore = props.mirroredGraphStore
        }
    }
}
