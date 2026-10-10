package tech.kzen.auto.client.objects.document.job.display.output

import emotion.react.css
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.objects.document.job.display.WorkerDisplayDefault
import tech.kzen.auto.client.objects.document.job.display.WorkerDisplayProps
import tech.kzen.auto.client.objects.document.job.display.WorkerDisplayWrapper
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.Color
import web.cssom.em


external interface WriteWorkerDisplayProps: WorkerDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper
    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}


/**
 * The ordinary Worker card, saying in its header what `Write` is writing and how much is on disk, and under it
 * each live edit that discarded files it had open.
 */
@Suppress("unused")
class WriteWorkerDisplay(
    props: WriteWorkerDisplayProps
):
    RPureComponent<WriteWorkerDisplayProps, State>(props)
{
    companion object {
        private val noticeColor = Color("#ed6c02")
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
            WriteWorkerDisplay::class.react {
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
            statusText = OutputProgressText::write
            bodyBefore = { it.renderDiscards() }
        }
    }


    private fun ChildrenBuilder.renderDiscards() {
        val notices = OutputProgressText.discards(props.common.progress)
        for ((index, notice) in notices.withIndex()) {
            div {
                key = Key("$index")
                css {
                    marginBottom = 0.5.em
                    fontSize = 0.85.em
                    color = noticeColor
                }
                +notice
            }
        }
    }
}
