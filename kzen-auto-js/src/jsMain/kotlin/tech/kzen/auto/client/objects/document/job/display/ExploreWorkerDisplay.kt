package tech.kzen.auto.client.objects.document.job.display

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewManager
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface ExploreWorkerDisplayProps: WorkerDisplayProps {
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var attributeViewManager: AttributeViewManager.Wrapper
    var clientStateGlobal: ClientStateGlobal
    var restClient: ClientRestApi
    var mirroredGraphStore: MirroredGraphStore
}


//---------------------------------------------------------------------------------------------------------------------
// Display for a Worker that persists a whole random-access result (ExploreWorker): the default card plus a
// "Download" button linking to the notation-resolved /job/download endpoint (table.csv), rendered into the card
// body via WorkerDisplayDefault.bodyExtra. The link is a pure function of the Worker's location (self-injected
// restClient) gated on the persisted row count from common.progress, so it stays valid AFTER the run ends — the
// whole point of a report. No run state, no controller coupling (see CC-17).
@Suppress("unused")
class ExploreWorkerDisplay(
    props: ExploreWorkerDisplayProps
):
    RPureComponent<ExploreWorkerDisplayProps, State>(props)
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
            ExploreWorkerDisplay::class.react {
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
            this.common = props.common
            this.bodyExtra = { bodyBuilder -> bodyBuilder.renderExploreDownload() }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Download the whole accumulated result set as table.csv — a plain <a href> to the notation-resolved
    // /job/download endpoint, shown only once the persisted table has rows (survives the run settling). Mirrors
    // Report's OutputTableController download button.
    private fun ChildrenBuilder.renderExploreDownload() {
        val rowCount = props.common.progress?.longValue("count") ?: 0L
        if (rowCount <= 0L) {
            return
        }

        val downloadLink = props.restClient.linkJobDownload(props.common.objectLocation)

        div {
            css {
                marginTop = 0.5.em
            }

            a {
                css {
                    textDecoration = None.none
                }

                href = downloadLink

                Button {
                    variant = ButtonVariant.outlined
                    size = Size.small

                    icon("material-symbols:cloud-download") {
                        style = unsafeJso {
                            marginRight = 0.25.em
                        }
                    }

                    +"Download"
                }
            }
        }
    }
}
