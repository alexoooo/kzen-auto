package tech.kzen.auto.client.objects.document.job.display

import react.ChildrenBuilder
import react.State
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.objects.document.job.preview.PreviewNode
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore


//---------------------------------------------------------------------------------------------------------------------
external interface PreviewWorkerDisplayProps: WorkerDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper
    var clientStateGlobal: ClientStateGlobal
    var restClient: ClientRestApi
    var mirroredGraphStore: MirroredGraphStore
}


//---------------------------------------------------------------------------------------------------------------------
// Display for a preview-serving Worker (PreviewWorker / PivotWorker): the default card plus its live sample
// ([PreviewSampleView]), rendered into the card body via WorkerDisplayDefault.bodyExtra — the exact
// RunStepDisplay-wraps-ScriptStepDisplayDefault composition.
@Suppress("unused")
class PreviewWorkerDisplay(
    props: PreviewWorkerDisplayProps
):
    RPureComponent<PreviewWorkerDisplayProps, State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        private val attributeViewManager: AttributeViewManager.Wrapper,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val restClient: ClientRestApi,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        WorkerDisplayWrapper(objectLocation)
    {
        override fun ChildrenBuilder.child(block: WorkerDisplayProps.() -> Unit) {
            PreviewWorkerDisplay::class.react {
                this.attributeEditorManager = this@Wrapper.attributeEditorManager
                this.attributeViewManager = this@Wrapper.attributeViewManager
                this.clientStateGlobal = this@Wrapper.clientStateGlobal
                this.restClient = this@Wrapper.restClient
                this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        WorkerDisplayDefault::class.react {
            this.attributeEditorManager = props.attributeEditorManager
            this.attributeViewManager = props.attributeViewManager
            this.clientStateGlobal = props.clientStateGlobal
            this.mirroredGraphStore = props.mirroredGraphStore
            val progress = props.common.progress
            this.common = props.common.copy(progress = progress?.copy(
                progressMap = progress.progressMap - setOf(PreviewNode.limitedKey, JobConventions.progressCountKey)))
            this.bodyExtra = { bodyBuilder ->
                with(bodyBuilder) {
                    PreviewSampleView::class.react {
                        workerLocation = props.common.objectLocation
                        this.progress = props.common.progress
                        active = props.common.active
                        clientStateGlobal = props.clientStateGlobal
                        restClient = props.restClient
                    }
                }
            }
        }
    }
}
